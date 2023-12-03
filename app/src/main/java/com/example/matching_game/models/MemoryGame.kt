package com.example.matching_game.models

import com.example.matching_game.utils.DEFAULT_ICONS

class MemoryGame(private val boardSize: BoardSize,  private val customImages: List<String>?){

    val cards: List<MemoryCard>
    var numPairsFound = 0

    private var numCardFlips = 0
    private var indexofSelectedCard: Int? = null

    init {
        if(customImages == null)
        {
            val chosenImages = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
            val randomizedImages : List<Int> = (chosenImages+chosenImages).shuffled()
            cards = randomizedImages.map{ MemoryCard(it) }
        }

        else
        {
            val randomizedImages :List<String> = (customImages + customImages).shuffled()
            cards = randomizedImages.map{ MemoryCard(it.hashCode(),it) }
        }
    }

    fun flipCard(position: Int): Boolean {
        numCardFlips++
        val card = cards[position]
        var foundMatch = false

        if(indexofSelectedCard == null)
        {
            //Means 0 or 2 cards previously flipped over
            restoreCards()
            indexofSelectedCard = position
        }
        else
        {
            //Means one card was previously flipped
            foundMatch = checkForMatch(indexofSelectedCard!!, position)
            indexofSelectedCard = null
        }
        card.isFaceUp = !card.isFaceUp
        return foundMatch
    }

    private fun checkForMatch(position1: Int, position2: Int): Boolean {
        if(cards[position1].identifier != cards[position2].identifier)
        {
            return false
        }
        cards[position1].isMatch = true
        cards[position2].isMatch = true
        numPairsFound++
        return true

    }

    private fun restoreCards() {
        for (card in cards)
        {
            if(!card.isMatch)
            {
                card.isFaceUp= false

            }
        }
    }

    fun haveWonGame(): Boolean {
        return numPairsFound == boardSize.getNumPairs()
    }

    fun isCardFaceUp(position: Int): Boolean {
        return cards[position].isFaceUp

    }

    fun getNumMoves(): Int {
        return numCardFlips / 2
    }
}
