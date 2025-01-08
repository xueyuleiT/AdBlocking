/**
 * amagi and lisonge <https://github.com/gkd-kit/gkd>
 * Copyright (C) 2024 amagi and lisonge
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ps.gkd.service

import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ps.gkd.META
import com.ps.gkd.app
import com.ps.gkd.appScope
import com.ps.gkd.data.ActionLog
import com.ps.gkd.data.ActivityLog
import com.ps.gkd.data.AppRule
import com.ps.gkd.data.GlobalRule
import com.ps.gkd.data.ResolvedRule
import com.ps.gkd.data.SubsConfig
import com.ps.gkd.db.DbSet
import com.ps.gkd.isActivityVisible
import com.ps.gkd.util.RuleSummary
import com.ps.gkd.util.actionCountFlow
import com.ps.gkd.util.getDefaultLauncherActivity
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.ruleSummaryFlow
import com.ps.gkd.util.storeFlow

data class TopActivity(
    val appId: String = "",
    val activityId: String? = null,
    val number: Int = 0
) {
    fun format(): String {
        return "${appId}/${activityId}/${number}"
    }

    fun sameAs(other: TopActivity): Boolean {
        return appId == other.appId && activityId == other.activityId
    }
}

val topActivityFlow = MutableStateFlow(TopActivity())
private val activityLogMutex by lazy { Mutex() }

private var activityLogCount = 0
private var lastActivityChangeTime = 0L
fun updateTopActivity(topActivity: TopActivity) {
    val isSameActivity = topActivityFlow.value.sameAs(topActivity)
    if (isSameActivity) {
        if (topActivityFlow.value.number == topActivity.number) {
            return
        }
        if (isActivityVisible() && topActivity.appId == META.appId) {
            return
        }
        val t = System.currentTimeMillis()
        if (t - lastActivityChangeTime < 1500) {
            return
        }
    }
    if (storeFlow.value.enableActivityLog) {
        appScope.launchTry(Dispatchers.IO) {
            activityLogMutex.withLock {
                DbSet.activityLogDao.insert(
                    ActivityLog(
                        appId = topActivity.appId,
                        activityId = topActivity.activityId
                    )
                )
                activityLogCount++
                if (activityLogCount % 100 == 0) {
                    DbSet.activityLogDao.deleteKeepLatest()
                }
            }
        }
    }
    LogUtils.d(
        "${topActivityFlow.value.format()} -> ${topActivity.format()}"
    )
    topActivityFlow.value = topActivity
    lastActivityChangeTime = System.currentTimeMillis()
}

class ActivityRule(
    val appRules: List<AppRule> = emptyList(),
    val globalRules: List<GlobalRule> = emptyList(),
    val topActivity: TopActivity = TopActivity(),
    val ruleSummary: RuleSummary = RuleSummary(),
) {
    val currentRules = (appRules + globalRules).sortedBy { it.order }
    val hasPriorityRule = currentRules.size > 1 && currentRules.any { it.priorityEnabled }
    val activePriority: Boolean
        get() = hasPriorityRule && currentRules.any { it.isPriority() }
    val priorityRules: List<ResolvedRule>
        get() = if (hasPriorityRule) {
            currentRules.sortedBy { if (it.isPriority()) 0 else 1 }
        } else {
            currentRules
        }
    val skipMatch: Boolean
        get() {
            return currentRules.all { r -> !r.status.ok }
        }
    val skipConsumeEvent: Boolean
        get() {
            return currentRules.all { r -> !r.status.alive }
        }
}

val activityRuleFlow by lazy { MutableStateFlow(ActivityRule()) }

private var lastTopActivity: TopActivity = topActivityFlow.value

private fun getFixTopActivity(): TopActivity {
    val top = topActivityFlow.value
    if (top.activityId == null) {
        if (lastTopActivity.appId == top.appId) {
            // 当从通知栏上拉返回应用, 从锁屏返回 等时, activityId 的无障碍事件不会触发, 此时复用上一次获得的 activityId 填充
            updateTopActivity(lastTopActivity)
        }
    } else {
        // 仅保留最近的有 activityId 的单个 TopActivity
        lastTopActivity = top
    }
    return topActivityFlow.value
}

fun getAndUpdateCurrentRules(): ActivityRule {
    val topActivity = getFixTopActivity()
    val oldActivityRule = activityRuleFlow.value
    val allRules = ruleSummaryFlow.value
    val idChanged = topActivity.appId != oldActivityRule.topActivity.appId
    val topChanged = idChanged || oldActivityRule.topActivity != topActivity
    val ruleChanged = oldActivityRule.ruleSummary !== allRules
    if (topChanged || ruleChanged) {
        val t = System.currentTimeMillis()
        val newActivityRule = ActivityRule(
            ruleSummary = allRules,
            topActivity = topActivity,
            appRules = (allRules.appIdToRules[topActivity.appId] ?: emptyList()).filter { rule ->
                rule.matchActivity(topActivity.appId, topActivity.activityId)
            },
            globalRules = ruleSummaryFlow.value.globalRules.filter { r ->
                r.matchActivity(topActivity.appId, topActivity.activityId)
            },
        )
        if (idChanged) {
            appChangeTime = t
            allRules.globalRules.forEach { r ->
                r.actionDelayTriggerTime = 0
                r.actionCount.value = 0
                r.matchChangedTime = t
            }
            allRules.appIdToRules[oldActivityRule.topActivity.appId]?.forEach { r ->
                r.actionDelayTriggerTime = 0
                r.actionCount.value = 0
                r.matchChangedTime = t
            }
            newActivityRule.appRules.forEach { r ->
                r.actionDelayTriggerTime = 0
                r.actionCount.value = 0
                r.matchChangedTime = t
            }
        } else {
            newActivityRule.currentRules.forEach { r ->
                if (r.resetMatchTypeWhenActivity) {
                    r.actionDelayTriggerTime = 0
                    r.actionCount.value = 0
                }
                if (!oldActivityRule.currentRules.contains(r)) {
                    // 新增规则
                    r.matchChangedTime = t
                }
            }
        }
        activityRuleFlow.value = newActivityRule
    }
    return activityRuleFlow.value
}

var lastTriggerRule: ResolvedRule? = null

@Volatile
var lastTriggerTime = 0L

@Volatile
var appChangeTime = 0L

var launcherActivity = TopActivity("")
val launcherAppId: String
    get() = launcherActivity.appId

fun updateLauncherAppId() {
    launcherActivity = app.packageManager.getDefaultLauncherActivity()
}

val clickLogMutex by lazy { Mutex() }
suspend fun insertClickLog(rule: ResolvedRule) {
    clickLogMutex.withLock {
        actionCountFlow.update { it + 1 }
        val actionLog = ActionLog(
            appId = topActivityFlow.value.appId,
            activityId = topActivityFlow.value.activityId,
            subsId = rule.subsItem.id,
            subsVersion = rule.rawSubs.version,
            groupKey = rule.g.group.key,
            groupType = when (rule) {
                is AppRule -> SubsConfig.AppGroupType
                is GlobalRule -> SubsConfig.GlobalGroupType
            },
            ruleIndex = rule.index,
            ruleKey = rule.key,
        )
        DbSet.actionLogDao.insert(actionLog)
        if (actionCountFlow.value % 100 == 0L) {
            DbSet.actionLogDao.deleteKeepLatest()
        }
    }
}
