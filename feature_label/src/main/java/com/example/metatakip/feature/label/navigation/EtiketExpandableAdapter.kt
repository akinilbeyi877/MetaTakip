// File: EtiketExpandableAdapter.kt
package com.example.metatakip.feature.label.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.CheckBox
import android.widget.TextView
import com.example.metatakip.feature.label.R
import com.example.metatakip.feature_data.label.EtiketManager.EtiketBileseni
import com.example.metatakip.feature_data.label.EtiketManager.EtiketKaynak

class EtiketExpandableAdapter(
    private val context: Context,
    private val groups: Map<EtiketKaynak, List<EtiketBileseni>>,
    private val modelFieldCounts: Map<EtiketKaynak, Int>,
    private val onChanged: () -> Unit
) : BaseExpandableListAdapter() {

    private val groupKeys = groups.keys.toList()

    override fun getGroupCount(): Int = groupKeys.size

    override fun getChildrenCount(groupPosition: Int): Int =
        groups[groupKeys[groupPosition]]?.size ?: 0

    override fun getGroup(groupPosition: Int): Any = groupKeys[groupPosition]

    override fun getChild(groupPosition: Int, childPosition: Int): Any =
        groups[groupKeys[groupPosition]]!![childPosition]

    override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()

    override fun getChildId(groupPosition: Int, childPosition: Int): Long =
        (groupPosition * 1000 + childPosition).toLong()

    override fun hasStableIds(): Boolean = false

    override fun getGroupView(
        groupPosition: Int,
        isExpanded: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {

        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_etiket_group, parent, false)

        val kaynak = groupKeys[groupPosition]
        val list = groups[kaynak].orEmpty()

        val secili = list.count { it.secili }
        val gosterilen = list.size
        val modelToplam = modelFieldCounts[kaynak] ?: gosterilen

        val tv = view.findViewById<TextView>(R.id.tvGroupTitle)
        tv.text = "📂 ${kaynak.name} ($secili / $gosterilen | Model: $modelToplam)"

        return view
    }

    override fun getChildView(
        groupPosition: Int,
        childPosition: Int,
        isLastChild: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {

        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_etiket_child, parent, false)

        val chk = view.findViewById<CheckBox>(R.id.chkItem)
        val tv = view.findViewById<TextView>(R.id.tvTitle)

        val item = getChild(groupPosition, childPosition) as EtiketBileseni

        tv.text = item.baslik

        chk.setOnCheckedChangeListener(null)
        chk.isChecked = item.secili
        chk.setOnCheckedChangeListener { _, checked ->
            item.secili = checked
            onChanged()
        }

        return view
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true
}