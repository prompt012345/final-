package com.bubblesms.app

import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bubblesms.app.data.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(private var items: MutableList<ChatMessage>, private val onLongPress:(Int)->Unit): RecyclerView.Adapter<MessageAdapter.BubbleVH>() {
    companion object { private const val TYPE_SENT=1; private const val TYPE_RECEIVED=2 }
    class BubbleVH(v:View):RecyclerView.ViewHolder(v){ val text:TextView=v.findViewById(R.id.bubbleText); val meta:TextView=v.findViewById(R.id.bubbleMeta); val reaction:TextView=v.findViewById(R.id.bubbleReaction) }
    fun update(v:List<ChatMessage>){items=v.toMutableList();notifyDataSetChanged()}
    override fun getItemViewType(p:Int)=if(items[p].isSent) TYPE_SENT else TYPE_RECEIVED
    override fun onCreateViewHolder(p:ViewGroup,t:Int)=BubbleVH(LayoutInflater.from(p.context).inflate(if(t==TYPE_SENT)R.layout.item_message_sent else R.layout.item_message_received,p,false))
    override fun onBindViewHolder(h:BubbleVH,p:Int){val m=items[p];h.text.text=m.body;h.meta.text=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(m.timestamp)) + if(m.pinned) "  • épinglé" else "";h.reaction.text=m.reaction;h.reaction.visibility=if(m.reaction.isBlank())View.GONE else View.VISIBLE;h.itemView.setOnLongClickListener{onLongPress(p);true}}
    override fun getItemCount()=items.size
}
