package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.CityStats
import com.unciv.logic.city.StatTreeNode
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Building
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import java.text.DecimalFormat
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class CityInfoTable(private val cityScreen: CityScreen) : Table(BaseScreen.skin) {
    private val pad = 10f

    private val showConstructionsTableButton = "Show construction queue".toTextButton()
    private val scrollPane: ScrollPane
    private val innerTable = Table(skin)

    init {
        align(Align.topLeft)

        showConstructionsTableButton.onClick {
            cityScreen.showConstructionsTable = true
            cityScreen.update()
        }

        innerTable.width = cityScreen.stage.width / 4
        innerTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))
        scrollPane = ScrollPane(innerTable.addBorder(2f, Color.WHITE))
        scrollPane.setOverscroll(false, false)

        add(showConstructionsTableButton).left().padLeft(pad).padBottom(pad).row()
        add(scrollPane).left().row()
    }

    internal fun update() {
        val cityInfo = cityScreen.city

        innerTable.clear()

        innerTable.apply {
            addBuildingsInfo(cityInfo)
            addStatInfo()
            addGreatPersonPointInfo(cityInfo)
        }

        getCell(scrollPane).maxHeight(stage.height - showConstructionsTableButton.height - pad - 10f)
        pack()
    }

    private fun Table.addCategory(category: String, showHideTable: Table) {
        val categoryWidth = cityScreen.stage.width / 4
        val expander = ExpanderTab(category, persistenceID = "CityInfo") {
            it.add(showHideTable).minWidth(categoryWidth)
        }
        addSeparator()
        add(expander).minWidth(categoryWidth).expandX().fillX().row()
    }

    private fun addBuildingInfo(building: Building, destinationTable: Table) {
        val icon = ImageGetter.getConstructionImage(building.name).surroundWithCircle(30f)
        val isFree = building.name in cityScreen.city.civInfo.civConstructions.getFreeBuildings(cityScreen.city.id)
        val displayName = if (isFree) "{${building.name}} ({Free})" else building.name
        val buildingNameAndIconTable = ExpanderTab(displayName, Constants.defaultFontSize, icon, false, 5f) {
            val detailsString = building.getDescription(cityScreen.city, false)
            it.add(detailsString.toLabel().apply { wrap = true })
                .width(cityScreen.stage.width / 4 - 2 * pad).row() // when you set wrap, then you need to manually set the size of the label
            if (building.isSellable() && !isFree) {
                val sellAmount = cityScreen.city.getGoldForSellingBuilding(building.name)
                val sellBuildingButton = "Sell for [$sellAmount] gold".toTextButton()
                it.add(sellBuildingButton).pad(5f).row()

                sellBuildingButton.onClick(UncivSound.Coin) {
                    sellBuildingButton.disable()
                    cityScreen.closeAllPopups()

                    YesNoPopup("Are you sure you want to sell this [${building.name}]?".tr(),
                        {
                            cityScreen.city.sellBuilding(building.name)
                            cityScreen.update()
                        }, cityScreen,
                        {
                            cityScreen.update()
                        }).open()
                }
                if (cityScreen.city.hasSoldBuildingThisTurn && !cityScreen.city.civInfo.gameInfo.gameParameters.godMode
                    || cityScreen.city.isPuppet
                    || !UncivGame.Current.worldScreen.isPlayersTurn || !cityScreen.canChangeState)
                    sellBuildingButton.disable()
            }
            it.addSeparator()
        }
        destinationTable.add(buildingNameAndIconTable).pad(5f).fillX().row()
    }

    private fun Table.addBuildingsInfo(cityInfo: CityInfo) {
        val wonders = mutableListOf<Building>()
        val specialistBuildings = mutableListOf<Building>()
        val otherBuildings = mutableListOf<Building>()

        for (building in cityInfo.cityConstructions.getBuiltBuildings()) {
            when {
                building.isAnyWonder() -> wonders.add(building)
                !building.newSpecialists().isEmpty() -> specialistBuildings.add(building)
                else -> otherBuildings.add(building)
            }
        }

        if (wonders.isNotEmpty()) {
            val wondersTable = Table()
            addCategory("Wonders", wondersTable)
            for (building in wonders) addBuildingInfo(building, wondersTable)
        }

        if (specialistBuildings.isNotEmpty()) {
            val specialistBuildingsTable = Table()
            addCategory("Specialist Buildings", specialistBuildingsTable)

            for (building in specialistBuildings) {
                addBuildingInfo(building, specialistBuildingsTable)
                val specialistIcons = Table()
                specialistIcons.row().size(20f).pad(5f)
                for ((specialistName, amount) in building.newSpecialists()) {
                    val specialist = cityInfo.getRuleset().specialists[specialistName]
                        ?: continue // probably a mod that doesn't have the specialist defined yet
                    for (i in 0 until amount)
                        specialistIcons.add(ImageGetter.getSpecialistIcon(specialist.colorObject)).size(20f)
                }

                specialistBuildingsTable.add(specialistIcons).pad(0f).row()
            }
        }

        if (otherBuildings.isNotEmpty()) {
            val regularBuildingsTable = Table()
            addCategory("Buildings", regularBuildingsTable)
            for (building in otherBuildings) addBuildingInfo(building, regularBuildingsTable)
        }
    }

    private fun addStatsToHashmap(statTreeNode: StatTreeNode, hashMap: HashMap<String, Float>, stat:Stat,
                                  showDetails:Boolean, indentation:Int=0) {
        for ((name, child) in statTreeNode.children) {
            val statAmount = child.totalStats[stat]
            if (statAmount == 0f) continue
            hashMap["- ".repeat(indentation) + name.tr()] = statAmount
            if (showDetails) addStatsToHashmap(child, hashMap, stat, showDetails, indentation + 1)
        }
    }

    private fun Table.addStatInfo() {
        val cityStats = cityScreen.city.cityStats

        for (stat in Stat.values()) {
            val statValuesTable = Table()
            statValuesTable.touchable = Touchable.enabled
            addCategory(stat.name, statValuesTable)

            updateStatValuesTable(stat, cityStats, statValuesTable)
        }
    }

    private fun updateStatValuesTable(
        stat: Stat,
        cityStats: CityStats,
        statValuesTable: Table,
        showDetails:Boolean = false
    ) {
        statValuesTable.clear()
        statValuesTable.defaults().pad(2f)
        statValuesTable.onClick {
            updateStatValuesTable(
                stat,
                cityStats,
                statValuesTable,
                !showDetails
            )
        }

        val relevantBaseStats = LinkedHashMap<String, Float>()

        if (stat != Stat.Happiness)
            addStatsToHashmap(cityStats.baseStatTree, relevantBaseStats, stat, showDetails)
        else relevantBaseStats.putAll(cityStats.happinessList)
        for (key in relevantBaseStats.keys.toList())
            if (relevantBaseStats[key] == 0f) relevantBaseStats.remove(key)

        if (relevantBaseStats.isEmpty()) return

        statValuesTable.add("Base values".toLabel(fontSize = FONT_SIZE_STAT_INFO_HEADER)).pad(4f)
            .colspan(2).row()
        var sumOfAllBaseValues = 0f
        for (entry in relevantBaseStats) {
            val specificStatValue = entry.value
            if (!entry.key.startsWith('-'))
                sumOfAllBaseValues += specificStatValue
            statValuesTable.add(entry.key.toLabel()).left()
            statValuesTable.add(specificStatValue.toOneDecimalLabel()).row()
        }
        statValuesTable.addSeparator()
        statValuesTable.add("Total".toLabel())
        statValuesTable.add(sumOfAllBaseValues.toOneDecimalLabel()).row()

        val relevantBonuses = LinkedHashMap<String, Float>()
        addStatsToHashmap(cityStats.statPercentBonusTree, relevantBonuses, stat, showDetails)

        val totalBonusStats = cityStats.statPercentBonusTree.totalStats
        if (totalBonusStats[stat] != 0f) {
            statValuesTable.add("Bonuses".toLabel(fontSize = FONT_SIZE_STAT_INFO_HEADER)).colspan(2)
                .padTop(20f).row()
            for ((source, bonusAmount) in relevantBonuses) {
                statValuesTable.add(source.toLabel()).left()
                statValuesTable.add(bonusAmount.toPercentLabel()).row() // negative bonus
            }
            statValuesTable.addSeparator()
            statValuesTable.add("Total".toLabel())
            statValuesTable.add(totalBonusStats[stat].toPercentLabel()).row() // negative bonus


            statValuesTable.add("Final".toLabel(fontSize = FONT_SIZE_STAT_INFO_HEADER)).colspan(2)
                .padTop(20f).row()
            var finalTotal = 0f
            for (entry in cityStats.finalStatList) {
                val specificStatValue = entry.value[stat]
                finalTotal += specificStatValue
                if (specificStatValue == 0f) continue
                statValuesTable.add(entry.key.toLabel())
                statValuesTable.add(specificStatValue.toOneDecimalLabel()).row()
            }
            statValuesTable.addSeparator()
            statValuesTable.add("Total".toLabel())
            statValuesTable.add(finalTotal.toOneDecimalLabel()).row()
        }

        statValuesTable.pack()
        val toggleButtonChar = if (showDetails) "-" else "+"
        val toggleButton = toggleButtonChar.toLabel().apply { setAlignment(Align.center) }
            .surroundWithCircle(25f, color = ImageGetter.getBlue())
            .surroundWithCircle(27f, false)
        statValuesTable.addActor(toggleButton)
        toggleButton.setPosition(0f, statValuesTable.height, Align.topLeft)

        statValuesTable.padBottom(4f)
    }

    private fun Table.addGreatPersonPointInfo(cityInfo: CityInfo) {
        val greatPersonPoints = cityInfo.getGreatPersonPointsForNextTurn()
        val allGreatPersonNames = greatPersonPoints.asSequence().flatMap { it.value.keys }.distinct()
        for (greatPersonName in allGreatPersonNames) {
            val expanderName = "[$greatPersonName] points"
            val greatPersonTable = Table()
            addCategory(expanderName, greatPersonTable)
            for ((source, gppCounter) in greatPersonPoints) {
                val gppPointsFromSource = gppCounter[greatPersonName]!!
                if (gppPointsFromSource == 0) continue
                greatPersonTable.add(source.toLabel()).padRight(10f)
                greatPersonTable.add(gppPointsFromSource.toLabel()).row()
            }
        }
    }

    companion object {
        private const val FONT_SIZE_STAT_INFO_HEADER = 22
        
        private fun Float.toPercentLabel() =
            "${if (this>0f) "+" else ""}${DecimalFormat("0.#").format(this)}%".toLabel()
        private fun Float.toOneDecimalLabel() =
            DecimalFormat("0.#").format(this).toLabel()
    }

}
