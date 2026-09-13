package com.bubblesms.app
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bubblesms.app.data.GroupMessage
import java.text.SimpleDateFormat
import java.util.*
class GroupMessageAdapter(private var items:List<GroupMessage>, private val onLongPress:(Int)->Unit):RecyclerView.Adapter<GroupMessageAdapter.VH>(){
 class VH(v:View):RecyclerView.ViewHolder(v){val sender:TextView=v.findViewById(R.id.groupMsgSender);val body:TextView=v.findViewById(R.id.groupMsgBody);val meta:TextView=v.findViewById(R.id.groupMsgMeta);val reaction:TextView=v.findViewById(R.id.groupMsgReaction)}
 override fun onCreateViewHolder(p:ViewGroup,t:Int)=VH(LayoutInflater.from(p.context).inflate(R.layout.item_group_message,p,false))
 override fun onBindViewHolder(h:VH,pos:Int){val m=items[pos];h.sender.text=if(m.isSent)"Moi" else m.senderPseudo;h.body.text=m.body;h.meta.text=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(m.timestamp))+(if(m.pinned)" • épinglé" else "");h.reaction.text=m.reaction;h.reaction.visibility=if(m.reaction.isBlank())View.GONE else View.VISIBLE;h.itemView.setOnLongClickListener{onLongPress(pos);true}}
 override fun getItemCount()=items.size
 fun update(v:List<GroupMessage>){items=v;notifyDataSetChanged()}
}
