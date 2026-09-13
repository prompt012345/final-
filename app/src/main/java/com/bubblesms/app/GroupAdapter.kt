package com.bubblesms.app
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bubblesms.app.data.Group
class GroupAdapter(private val items:List<Group>,private val onClick:(Group)->Unit):RecyclerView.Adapter<GroupAdapter.VH>(){
 class VH(v:View):RecyclerView.ViewHolder(v){val name:TextView=v.findViewById(R.id.groupItemName);val info:TextView=v.findViewById(R.id.groupItemInfo);val avatar:TextView=v.findViewById(R.id.groupItemAvatar)}
 override fun onCreateViewHolder(p:ViewGroup,t:Int)=VH(LayoutInflater.from(p.context).inflate(R.layout.item_group,p,false))
 override fun onBindViewHolder(h:VH,pos:Int){val g=items[pos];h.name.text=g.name;h.info.text="${g.members.size+1} membres";h.avatar.text=g.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#";h.itemView.setOnClickListener{onClick(g)}}
 override fun getItemCount()=items.size
}
