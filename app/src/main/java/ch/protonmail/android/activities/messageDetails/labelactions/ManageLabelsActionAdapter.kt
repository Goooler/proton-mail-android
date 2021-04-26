/*
 * Copyright (c) 2020 Proton Technologies AG
 *
 * This file is part of ProtonMail.
 *
 * ProtonMail is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonMail is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonMail. If not, see https://www.gnu.org/licenses/.
 */

package ch.protonmail.android.activities.messageDetails.labelactions

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ch.protonmail.android.databinding.ItemManageLabelsActionBinding
import timber.log.Timber

class ManageLabelsActionAdapter(
    private val clickListener: (ManageLabelItemUiModel) -> Unit,
    private val actionSheetType: ManageLabelsActionSheet.Type = ManageLabelsActionSheet.Type.LABEL
) : ListAdapter<ManageLabelItemUiModel, RecyclerView.ViewHolder>(ManageLabelItemDiffUtil()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = ItemManageLabelsActionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ManageLabelsViewHolder(
            view.textviewCheckboxManageLabelsTitle,
            view.checkboxManageLabelsActionIsChecked,
            view.root
        )

    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ManageLabelsViewHolder).bind(getItem(position), clickListener)
    }

    class ManageLabelsViewHolder(
        private val titleTextView: TextView,
        private val checkbox: CompoundButton,
        root: ConstraintLayout
    ) : RecyclerView.ViewHolder(root) {

        fun bind(
            model: ManageLabelItemUiModel,
            clickListener: (ManageLabelItemUiModel) -> Unit
        ) {
            Timber.v("Bind ManageLabelsViewHolder $model")
            itemView.setOnClickListener { clickListener(model) }
            titleTextView.apply {
                text = if (model.titleRes != null)
                    resources.getString(model.titleRes)
                else
                    model.title

                val iconDrawable = ResourcesCompat.getDrawable(resources, model.iconRes, null)
                iconDrawable?.setTint(model.colorInt)
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    iconDrawable,
                    null,
                    null,
                    null
                )
            }
            with(checkbox) {
                if (model.isChecked == null) {
                    isVisible = false
                } else {
                    isChecked = model.isChecked
                    isVisible = true
                }
            }
        }
    }
}
