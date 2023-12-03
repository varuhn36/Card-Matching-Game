package com.example.matching_game

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.matching_game.models.BoardSize
import kotlin.math.min

class ImagePickerAdapter(
    private val context: Context,
    private val imageUriList: List<Uri>,
    private val boardSize: BoardSize,
    private val imageClickListener: ImageClickListener
) : RecyclerView.Adapter<ImagePickerAdapter.ViewHolder>() {


    interface  ImageClickListener {
        fun onPlaceHolderClicked()
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.card_image, parent, false)
        var cardWidth = parent.width / boardSize.getWidth()
        var cardHeight = parent.height / boardSize.getHeight()
        val cardDimension = min(cardWidth, cardHeight)
        val layoutParams = view.findViewById<ImageView>(R.id.custom_image).layoutParams
        layoutParams.width = cardDimension
        layoutParams.height = cardDimension
        return ViewHolder(view)
    }

    override fun getItemCount() = boardSize.getNumPairs()

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if(position < imageUriList.size)
        {
            holder.bind(imageUriList[position])
        }
        else
        {
            holder.bind()
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val customImage = itemView.findViewById<ImageView>(R.id.custom_image)

        fun bind(uri: Uri) {
            customImage.setImageURI(uri)
            customImage.setOnClickListener(null)
        }

        fun bind() {
            customImage.setOnClickListener{
                imageClickListener.onPlaceHolderClicked()

            }
        }

    }

}
