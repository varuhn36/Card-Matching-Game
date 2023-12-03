package com.example.matching_game

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.matching_game.models.BoardSize
import com.example.matching_game.models.MemoryGame
import com.example.matching_game.models.UserImageList
import com.example.matching_game.utils.EXTRA_BOARD_SIZE
import com.example.matching_game.utils.EXTRA_GAME_NAME
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {

    companion object{
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 123

    }

    private lateinit var clRoot : CoordinatorLayout
    private lateinit var gameBoard : RecyclerView
    private lateinit var moves : TextView
    private lateinit var pairs : TextView
    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter: MemoryBoardAdapter

    private var customGameImages: List<String>? = null
    private var boardSize: BoardSize = BoardSize.EASY
    private val dataBase = Firebase.firestore
    private var gameName : String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        clRoot= findViewById(R.id.clRoot)
        gameBoard = findViewById(R.id.gameBoard)
        moves = findViewById(R.id.moves)
        pairs = findViewById(R.id.pairs)

        setupBoard()

    }



    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    private fun showAlertDialog(title: String, view: View?, positiveButtonClickListener: View.OnClickListener ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view).
            setNegativeButton("Cancel", null)
            .setPositiveButton("Ok") {_, _ ->
            positiveButtonClickListener.onClick(null)
        }.show()
    }

    private fun showCreationDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radio_group)

        showAlertDialog("Create your own game board!", boardSizeView, View.OnClickListener {
           val desiredBoardSize = when(radioGroupSize.checkedRadioButtonId) {
                R.id.set_game_easy_button -> BoardSize.EASY
                R.id.set_game_medium_button -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            val intent = Intent(this, CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSize)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        })
    }

    private fun showNewSizeDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radio_group)
        when(boardSize){
            BoardSize.EASY -> radioGroupSize.check(R.id.set_game_easy_button)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.set_game_medium_button)
            BoardSize.HARD -> radioGroupSize.check(R.id.set_game_hard_button)
        }

        showAlertDialog("Choose New Size", boardSizeView, View.OnClickListener {
            boardSize = when (radioGroupSize.checkedRadioButtonId){
                R.id.set_game_easy_button -> BoardSize.EASY
                R.id.set_game_medium_button -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            gameName = null
            customGameImages = null
            setupBoard()
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId)
        {
            R.id.menu_refresh -> {

                if(memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame())
                {
                    showAlertDialog("Are you sure you want to restart?", null, View.OnClickListener {
                        setupBoard()
                    })
                }
                else
                {
                    setupBoard()
                }
            }
            R.id.menu_update_size -> {
                showNewSizeDialog()
                return true
            }
            R.id.create_custom_game -> {
                showCreationDialog()
                return true
            }
            R.id.search_game -> {
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK)
        {
            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
            if(customGameName == null)
            {
                Log.e(TAG, "Got null game name")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun downloadGame(customGameName: String) {
        dataBase.collection("games").document(customGameName).get().addOnSuccessListener {document ->
            val userImageList = document.toObject(UserImageList::class.java)
            if(userImageList?.images == null)
            {
                Log.e(TAG, "Invalid Custom Game data from firestore")
                Snackbar.make(clRoot, "Sorry we couldn't find any game with that name", Snackbar.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            val numCards = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images

            for(imageUrl in userImageList.images)
            {
                Picasso.get().load(imageUrl).fetch()
            }
            gameName = customGameName
            Snackbar.make(clRoot, "You are now playing $customGameName !", Snackbar.LENGTH_LONG).show()
            setupBoard()

        }.addOnFailureListener { exception ->
            Log.e(TAG, "Exception when retrieving game", exception)
        }
    }

    private fun setupBoard() {
        supportActionBar?.title = gameName ?: getString(R.string.app_name)
        when(boardSize){
            BoardSize.EASY -> {
                moves.text = "Easy: 4 x 2"
                pairs.text = "Pairs: 0/4"
            }
            BoardSize.MEDIUM -> {
                moves.text = "Medium: 6 x 3"
                pairs.text = "Pairs: 0/9"
            }
            BoardSize.HARD -> {
                moves.text = "Easy: 6 x 4"
                pairs.text = "Pairs: 0/12"
            }
        }
        pairs.setTextColor(ContextCompat.getColor(this, R.color.no_progess_color))
        memoryGame = MemoryGame(boardSize, customGameImages)
        adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object: MemoryBoardAdapter.CardClickListener{
            override fun onCardClicked(position: Int) {
                updateGameWithFlip(position)
            }

        })
        gameBoard.adapter = adapter
        gameBoard.setHasFixedSize((true))
        gameBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    private fun showDownloadDialog() {
        val boardDownloadView = LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog("Find a Game", boardDownloadView, View.OnClickListener {
            val downloadGame = boardDownloadView.findViewById<EditText>(R.id.download_game)
            val gameToDownload = downloadGame.text.toString().trim()
            downloadGame(gameToDownload)

        })
    }

    private fun updateGameWithFlip(position: Int) {
        if(memoryGame.haveWonGame()){
            Snackbar.make(clRoot, "You already won!", Snackbar.LENGTH_LONG).show()
            return
        }

        if(memoryGame.isCardFaceUp(position)){
            Snackbar.make(clRoot, "Invalid move!", Snackbar.LENGTH_SHORT).show()
            return
        }
        if(memoryGame.flipCard(position))
        {
            Log.i(TAG, "Found a Match! Num Pairs Found: ${memoryGame.numPairsFound}")
            val color = ArgbEvaluator().evaluate(
                memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
                ContextCompat.getColor(this, R.color.no_progess_color),
                ContextCompat.getColor(this, R.color.full_progess_color)
                ) as Int
            pairs.setTextColor(color)
            pairs.text = "Pairs: ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"

            if(memoryGame.haveWonGame())
            {
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.RED, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.BLACK, Color.YELLOW)).oneShot()
                Snackbar.make(clRoot, "You win! Congrats", Snackbar.LENGTH_LONG).show()

            }
        }
        moves.text = "Moves: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()
    }
}