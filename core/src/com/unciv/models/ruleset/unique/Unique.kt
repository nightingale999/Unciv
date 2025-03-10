package com.unciv.models.ruleset.unique

import com.unciv.Constants
import com.unciv.logic.battle.CombatAction
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.CityInfo
import com.unciv.models.stats.Stats
import com.unciv.models.translations.*
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Ruleset
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class Unique(val text: String, val sourceObjectType: UniqueTarget? = null, val sourceObjectName: String? = null) {
    /** This is so the heavy regex-based parsing is only activated once per unique, instead of every time it's called
     *  - for instance, in the city screen, we call every tile unique for every tile, which can lead to ANRs */
    val placeholderText = text.getPlaceholderText()
    val params = text.removeConditionals().getPlaceholderParameters()
    val type = UniqueType.values().firstOrNull { it.placeholderText == placeholderText }

    val stats: Stats by lazy {
        val firstStatParam = params.firstOrNull { Stats.isStats(it) }
        if (firstStatParam == null) Stats() // So badly-defined stats don't crash the entire game
        else Stats.parse(firstStatParam)
    }
    val conditionals: List<Unique> = text.getConditionals()
    val isTriggerable = type != null && type.targetTypes.contains(UniqueTarget.Triggerable)
            // <for [amount] turns]> in effect makes any unique become a triggerable unique
            || conditionals.any { it.type == UniqueType.ConditionalTimedUnique }

    val allParams = params + conditionals.flatMap { it.params }

    val isLocalEffect = params.contains("in this city")
    val isAntiLocalEffect = params.contains("in other cities")

    fun hasFlag(flag: UniqueFlag) = type != null && type.flags.contains(flag)

    fun isOfType(uniqueType: UniqueType) = uniqueType == type

    fun conditionalsApply(civInfo: CivilizationInfo? = null, city: CityInfo? = null): Boolean {
        return conditionalsApply(StateForConditionals(civInfo, city))
    }

    fun conditionalsApply(state: StateForConditionals?): Boolean {
        if (state == null) return conditionals.isEmpty()
        if (state.ignoreConditionals) return true
        for (condition in conditionals) {
            if (!conditionalApplies(condition, state)) return false
        }
        return true
    }

    fun getDeprecationAnnotation(): Deprecated? = type?.getDeprecationAnnotation()

    fun getReplacementText(ruleset: Ruleset): String {
        val deprecationAnnotation = getDeprecationAnnotation() ?: return ""
        val replacementUniqueText = deprecationAnnotation.replaceWith.expression
        val deprecatedUniquePlaceholders = type!!.text.getPlaceholderParameters()
        val possibleUniques = replacementUniqueText.split(Constants.uniqueOrDelimiter)

        // Here, for once, we DO want the conditional placeholder parameters together with the regular ones,
        //  so we cheat the conditional detector by removing the '<'
        val finalPossibleUniques = ArrayList<String>()
        
        for (possibleUnique in possibleUniques) {
            for (parameter in possibleUnique.replace('<', ' ').getPlaceholderParameters()) {
                val parameterNumberInDeprecatedUnique =
                    deprecatedUniquePlaceholders.indexOf(
                        parameter.removePrefix("+").removePrefix("-")
                    )
                if (parameterNumberInDeprecatedUnique == -1) continue
                var replacementText = params[parameterNumberInDeprecatedUnique]
                if (parameter.startsWith('+')) replacementText = "+$replacementText"
                else if (parameter.startsWith('-')) replacementText = "-$replacementText"
                finalPossibleUniques +=
                    possibleUnique.replace("[$parameter]", "[$replacementText]")
            }
        }
        if (finalPossibleUniques.size == 1) return finalPossibleUniques.first()
        
        // filter out possible replacements that are obviously wrong
        val uniquesWithNoErrors = finalPossibleUniques.filter { 
            val unique = Unique(it)
            val errors = ruleset.checkUnique(unique, true, "",
                UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific, unique.type!!.targetTypes.first())
            errors.isEmpty()
        }
        if (uniquesWithNoErrors.size == 1) return uniquesWithNoErrors.first()
        
        val uniquesToUnify = if (uniquesWithNoErrors.isNotEmpty()) uniquesWithNoErrors else possibleUniques
        return uniquesToUnify.joinToString("\", \"")
    }

    private fun conditionalApplies(
        condition: Unique,
        state: StateForConditionals
    ): Boolean {

        fun ruleset() = state.civInfo!!.gameInfo.ruleSet
        val relevantTile by lazy { state.attackedTile
            ?: state.tile
            ?: state.unit?.getTile()
            ?: state.cityInfo?.getCenterTile()
        }
        val relevantUnit by lazy {
            if (state.ourCombatant != null && state.ourCombatant is MapUnitCombatant) state.ourCombatant.unit
            else state.unit
        }

        return when (condition.type) {
            // These are 'what to do' and not 'when to do' conditionals
            UniqueType.ConditionalTimedUnique -> true
            UniqueType.ConditionalConsumeUnit -> true
            
            UniqueType.ConditionalWar -> state.civInfo?.isAtWar() == true
            UniqueType.ConditionalNotWar -> state.civInfo?.isAtWar() == false
            UniqueType.ConditionalWithResource -> state.civInfo?.hasResource(condition.params[0]) == true
            UniqueType.ConditionalHappy ->
                state.civInfo != null && state.civInfo.statsForNextTurn.happiness >= 0
            UniqueType.ConditionalBetweenHappiness ->
                state.civInfo != null
                && condition.params[0].toInt() <= state.civInfo.happinessForNextTurn
                && state.civInfo.happinessForNextTurn < condition.params[1].toInt()
            UniqueType.ConditionalBelowHappiness -> 
                state.civInfo != null && state.civInfo.happinessForNextTurn < condition.params[0].toInt() 
            UniqueType.ConditionalGoldenAge ->
                state.civInfo != null && state.civInfo.goldenAges.isGoldenAge()
            UniqueType.ConditionalBeforeEra ->
                state.civInfo != null && state.civInfo.getEraNumber() < ruleset().eras[condition.params[0]]!!.eraNumber
            UniqueType.ConditionalStartingFromEra ->
                state.civInfo != null && state.civInfo.getEraNumber() >= ruleset().eras[condition.params[0]]!!.eraNumber
            UniqueType.ConditionalDuringEra ->
                state.civInfo != null && state.civInfo.getEraNumber() == ruleset().eras[condition.params[0]]!!.eraNumber
            UniqueType.ConditionalTech ->
                state.civInfo != null && state.civInfo.tech.isResearched(condition.params[0])
            UniqueType.ConditionalNoTech ->
                state.civInfo != null && !state.civInfo.tech.isResearched(condition.params[0])
            UniqueType.ConditionalPolicy ->
                state.civInfo != null && state.civInfo.policies.isAdopted(condition.params[0])
            UniqueType.ConditionalNoPolicy ->
                state.civInfo != null && !state.civInfo.policies.isAdopted(condition.params[0])
            UniqueType.ConditionalBuildingBuilt -> 
                state.civInfo != null && state.civInfo.cities.any { it.cityConstructions.containsBuildingOrEquivalent(condition.params[0]) }

            UniqueType.ConditionalCityWithBuilding -> 
                state.cityInfo != null && state.cityInfo.cityConstructions.containsBuildingOrEquivalent(condition.params[0])
            UniqueType.ConditionalCityWithoutBuilding -> 
                state.cityInfo != null && !state.cityInfo.cityConstructions.containsBuildingOrEquivalent(condition.params[0])
            UniqueType.ConditionalSpecialistCount ->
                state.cityInfo != null && state.cityInfo.population.getNumberOfSpecialists() >= condition.params[0].toInt()
            UniqueType.ConditionalFollowerCount ->
                state.cityInfo != null && state.cityInfo.religion.getFollowersOfMajorityReligion() >= condition.params[0].toInt()
            UniqueType.ConditionalWhenGarrisoned ->
                state.cityInfo != null && state.cityInfo.getCenterTile().militaryUnit != null && state.cityInfo.getCenterTile().militaryUnit!!.canGarrison()

            UniqueType.ConditionalVsCity -> state.theirCombatant?.matchesCategory("City") == true
            UniqueType.ConditionalVsUnits -> state.theirCombatant?.matchesCategory(condition.params[0]) == true
            UniqueType.ConditionalOurUnit ->
                relevantUnit?.matchesFilter(condition.params[0]) == true
            UniqueType.ConditionalUnitWithPromotion -> relevantUnit?.promotions?.promotions?.contains(params[0]) == true
            UniqueType.ConditionalUnitWithoutPromotion -> relevantUnit?.promotions?.promotions?.contains(params[0]) == false
            UniqueType.ConditionalAttacking -> state.combatAction == CombatAction.Attack
            UniqueType.ConditionalDefending -> state.combatAction == CombatAction.Defend
            UniqueType.ConditionalAboveHP ->
                state.ourCombatant != null && state.ourCombatant.getHealth() > condition.params[0].toInt()
            UniqueType.ConditionalBelowHP ->
                state.ourCombatant != null && state.ourCombatant.getHealth() < condition.params[0].toInt()
            
            UniqueType.ConditionalInTiles ->
                relevantTile?.matchesFilter(condition.params[0], state.civInfo) == true
            UniqueType.ConditionalFightingInTiles ->
                state.attackedTile?.matchesFilter(condition.params[0], state.civInfo) == true
            UniqueType.ConditionalInTilesAnd ->
                relevantTile!=null && relevantTile!!.matchesFilter(condition.params[0], state.civInfo)
                        && relevantTile!!.matchesFilter(condition.params[1], state.civInfo)
            UniqueType.ConditionalVsLargerCiv -> {
                val yourCities = state.civInfo?.cities?.size ?: 1
                val theirCities = state.theirCombatant?.getCivInfo()?.cities?.size ?: 0
                yourCities < theirCities
            }
            UniqueType.ConditionalForeignContinent ->
                state.civInfo != null && relevantTile != null
                    && (state.civInfo.cities.isEmpty()
                        || state.civInfo.getCapital().getCenterTile().getContinent()
                            != relevantTile!!.getContinent()
                    )
            UniqueType.ConditionalAdjacentUnit ->
                state.civInfo != null 
                && relevantUnit != null
                && relevantTile!!.neighbors.any {
                    it.militaryUnit != null
                    && it.militaryUnit != relevantUnit
                    && it.militaryUnit!!.civInfo == state.civInfo    
                    && it.militaryUnit!!.matchesFilter(condition.params[0])
                }

            UniqueType.ConditionalNeighborTiles ->
                relevantTile != null &&
                        relevantTile!!.neighbors.count {
                            it.matchesFilter(condition.params[2], state.civInfo)
                        } in (condition.params[0].toInt())..(condition.params[1].toInt())
            UniqueType.ConditionalNeighborTilesAnd ->
                relevantTile != null
                && relevantTile!!.neighbors.count {
                    it.matchesFilter(condition.params[2], state.civInfo) 
                    && it.matchesFilter(condition.params[3], state.civInfo)
                } in (condition.params[0].toInt())..(condition.params[1].toInt())

            UniqueType.ConditionalOnWaterMaps -> state.region?.continentID == -1
            UniqueType.ConditionalInRegionOfType -> state.region?.type == condition.params[0]
            UniqueType.ConditionalInRegionExceptOfType -> state.region?.type != condition.params[0]

            UniqueType.ConditionalFirstCivToResearch -> sourceObjectType == UniqueTarget.Tech
                    && state.civInfo != null
                    && state.civInfo.gameInfo.civilizations.none {
                it != state.civInfo && it.isMajorCiv() && it.hasTechOrPolicy(sourceObjectName!!)
            }

            else -> false
        }
    }

    override fun toString() = if (type == null) "\"$text\"" else "$type (\"$text\")"
}


class UniqueMap: HashMap<String, ArrayList<Unique>>() {
    //todo Once all untyped Uniques are converted, this should be  HashMap<UniqueType, *>
    // For now, we can have both map types "side by side" each serving their own purpose,
    // and gradually this one will be deprecated in favor of the other
    fun addUnique(unique: Unique) {
        if (!containsKey(unique.placeholderText)) this[unique.placeholderText] = ArrayList()
        this[unique.placeholderText]!!.add(unique)
    }

    fun getUniques(placeholderText: String): Sequence<Unique> {
        return this[placeholderText]?.asSequence() ?: emptySequence()
    }

    fun getUniques(uniqueType: UniqueType) = getUniques(uniqueType.placeholderText)

    fun getMatchingUniques(uniqueType: UniqueType, state: StateForConditionals) = getUniques(uniqueType)
        .filter { it.conditionalsApply(state) }

    fun getAllUniques() = this.asSequence().flatMap { it.value.asSequence() }
}

/** DOES NOT hold untyped uniques! */
class UniqueMapTyped: EnumMap<UniqueType, ArrayList<Unique>>(UniqueType::class.java) {
    fun addUnique(unique: Unique) {
        if(unique.type==null) return
        if (!containsKey(unique.type)) this[unique.type] = ArrayList()
        this[unique.type]!!.add(unique)
    }

    fun getUniques(uniqueType: UniqueType): Sequence<Unique> =
        this[uniqueType]?.asSequence() ?: sequenceOf()
}


class TemporaryUnique() {

    constructor(uniqueObject: Unique, turns: Int) : this() {
        unique = uniqueObject.text
        turnsLeft = turns
    }

    var unique: String = ""

    @delegate:Transient
    val uniqueObject: Unique by lazy { Unique(unique) }

    var turnsLeft: Int = 0
}