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
package com.ps.gkd.data

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.ps.gkd.R
import com.ps.gkd.getSafeString
import kotlinx.coroutines.Job
import com.ps.gkd.service.lastTriggerRule
import com.ps.gkd.service.lastTriggerTime
import li.songe.selector.MatchOption
import li.songe.selector.Selector

sealed class ResolvedRule(
    val rule: RawSubscription.RawRuleProps,
    val g: ResolvedGroup,
) {
    private val group = g.group
    val subsItem = g.subsItem
    val rawSubs = g.subscription
    val config = g.config
    val key = rule.key
    val index = group.rules.indexOfFirst { r -> r === rule }
    val excludeData = g.excludeData
    private val preKeys = (rule.preKeys ?: emptyList()).toSet()
    val matches =
        (rule.matches ?: emptyList()).map { s -> group.cacheMap[s] ?: Selector.parse(s) }
    val excludeMatches =
        (rule.excludeMatches ?: emptyList()).map { s -> group.cacheMap[s] ?: Selector.parse(s) }
    val anyMatches =
        (rule.anyMatches ?: emptyList()).map { s -> group.cacheMap[s] ?: Selector.parse(s) }

    private val resetMatch = rule.resetMatch ?: group.resetMatch
    val matchDelay = rule.matchDelay ?: group.matchDelay ?: 0L
    val actionDelay = rule.actionDelay ?: group.actionDelay ?: 0L
    private val matchTime = rule.matchTime ?: group.matchTime
    private val forcedTime = rule.forcedTime ?: group.forcedTime ?: 0L
    val matchOption = MatchOption(
        quickFind = rule.quickFind ?: group.quickFind ?: false,
        fastQuery = rule.fastQuery ?: group.fastQuery ?: false
    )
    val matchRoot = rule.matchRoot ?: group.matchRoot ?: false
    val order = rule.order ?: group.order ?: 0

    private val actionCdKey = rule.actionCdKey ?: group.actionCdKey
    private val actionCd = rule.actionCd ?: if (actionCdKey != null) {
        group.rules.find { r -> r.key == actionCdKey }?.actionCd
    } else {
        null
    } ?: group.actionCd ?: 1000L

    private val actionMaximumKey = rule.actionMaximumKey ?: group.actionMaximumKey
    private val actionMaximum = rule.actionMaximum ?: if (actionMaximumKey != null) {
        group.rules.find { r -> r.key == actionMaximumKey }?.actionMaximum
    } else {
        null
    } ?: group.actionMaximum

    private val hasSlowSelector by lazy {
        (matches + excludeMatches + anyMatches).any { s -> s.isSlow(matchOption) }
    }
    val priorityTime = rule.priorityTime ?: group.priorityTime ?: 0
    val priorityActionMaximum = rule.priorityActionMaximum ?: group.priorityActionMaximum ?: 1
    val priorityEnabled: Boolean
        get() = priorityTime > 0

    fun isPriority(): Boolean {
        if (!priorityEnabled) return false
        if (priorityActionMaximum <= actionCount.value) return false
        if (!status.ok) return false
        val t = System.currentTimeMillis()
        return t - matchChangedTime < priorityTime + matchDelay
    }

    val isSlow by lazy { preKeys.isEmpty() && (matchTime == null || matchTime > 10_000L) && hasSlowSelector }

    var groupToRules: Map<out RawSubscription.RawGroupProps, List<ResolvedRule>> = emptyMap()
        set(value) {
            field = value
            val selfGroupRules = field[group] ?: emptyList()
            val othersGroupRules =
                (group.scopeKeys ?: emptyList()).distinct().filter { k -> k != group.key }
                    .map { k ->
                        field.entries.find { e -> e.key.key == k }?.value ?: emptyList()
                    }.flatten()
            val groupRules = selfGroupRules + othersGroupRules

            // 共享次数
            if (actionMaximumKey != null) {
                val otherRule = groupRules.find { r -> r.key == actionMaximumKey }
                if (otherRule != null) {
                    actionCount = otherRule.actionCount
                }
            }
            // 共享 cd
            if (actionCdKey != null) {
                val otherRule = groupRules.find { r -> r.key == actionCdKey }
                if (otherRule != null) {
                    actionTriggerTime = otherRule.actionTriggerTime
                }
            }
            preRules = groupRules.filter { otherRule ->
                (otherRule.key != null) && preKeys.contains(
                    otherRule.key
                )
            }.toSet()
        }

    private var preRules = emptySet<ResolvedRule>()
    val hasNext = group.rules.any { r -> r.preKeys?.any { k -> k == rule.key } == true }

    var actionDelayTriggerTime = 0L
    var actionDelayJob: Job? = null
    fun checkDelay(): Boolean {
        if (actionDelay > 0 && actionDelayTriggerTime == 0L) {
            actionDelayTriggerTime = System.currentTimeMillis()
            return true
        }
        return false
    }

    fun checkForced(): Boolean {
        if (forcedTime <= 0) return false
        return System.currentTimeMillis() < matchChangedTime + matchDelay + forcedTime
    }

    private var actionTriggerTime = Value(0L)
    fun trigger() {
        actionTriggerTime.value = System.currentTimeMillis()
        lastTriggerTime = actionTriggerTime.value
        // 重置延迟点
        actionDelayTriggerTime = 0L
        actionCount.value++
        lastTriggerRule = this
    }

    var actionCount = Value(0)

    var matchChangedTime = 0L

    private val matchLimitTime = (matchTime ?: 0) + matchDelay

    val resetMatchTypeWhenActivity = when (resetMatch) {
        "app" -> false
        "activity" -> true
        else -> true
    }

    private val performer = ActionPerformer.getAction(rule.action ?: rule.position?.let {
        ActionPerformer.ClickCenter.action
    })

    fun performAction(
        context: AccessibilityService,
        node: AccessibilityNodeInfo
    ): ActionResult {
        return performer.perform(context, node, rule.position)
    }

    var matchDelayJob: Job? = null

    val status: RuleStatus
        get() {
            if (actionMaximum != null) {
                if (actionCount.value >= actionMaximum) {
                    return RuleStatus.Status1 // 达到最大执行次数
                }
            }
            if (preRules.isNotEmpty() && !preRules.any { it === lastTriggerRule }) {
                return RuleStatus.Status2 // 需要提前触发某个规则
            }
            val t = System.currentTimeMillis()
            if (matchDelay > 0 && t - matchChangedTime < matchDelay) {
                return RuleStatus.Status3 // 处于匹配延迟中
            }
            if (matchTime != null && t - matchChangedTime > matchLimitTime) {
                return RuleStatus.Status4 // 超出匹配时间
            }
            if (actionTriggerTime.value + actionCd > t) {
                return RuleStatus.Status5 // 处于冷却时间
            }
            if (actionDelayTriggerTime > 0) {
                if (actionDelayTriggerTime + actionDelay > t) {
                    return RuleStatus.Status6 // 处于触发延迟中
                }
            }
            return RuleStatus.StatusOk
        }

    fun statusText(): String {
        return "id:${subsItem.id}, v:${rawSubs.version}, type:${type}, gKey=${group.key}, gName:${group.name}, index:${index}, key:${key}, status:${status.name}"
    }

    abstract val type: String

    // 范围越精确, 优先级越高
    abstract fun matchActivity(appId: String, activityId: String? = null): Boolean
}

sealed class RuleStatus(val name: String) {
    data object StatusOk : RuleStatus("ok")
    data object Status1 : RuleStatus(getSafeString(R.string.max_execution_times_reached))
    data object Status2 : RuleStatus(getSafeString(R.string.need_to_click_a_rule_first))
    data object Status3 : RuleStatus(getSafeString(R.string.matching_delay))
    data object Status4 : RuleStatus(getSafeString(R.string.matching_time_exceeded))
    data object Status5 : RuleStatus(getSafeString(R.string.cooling_down))
    data object Status6 : RuleStatus(getSafeString(R.string.click_delay))

    val ok: Boolean
        get() = this === StatusOk

    val alive: Boolean
        get() = this !== Status1 && this !== Status2 && this !== Status4
}

fun getFixActivityIds(
    appId: String,
    activityIds: List<String>?,
): List<String> {
    if (activityIds == null || activityIds.isEmpty()) return emptyList()
    return activityIds.map { activityId ->
        if (activityId.startsWith('.')) { // .a.b.c -> com.x.y.x.a.b.c
            appId + activityId
        } else {
            activityId
        }
    }
}
