package com.example.mapbox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import model.Customers

class CustomerListAdapter(private val customerList: List<Customers>) :
    RecyclerView.Adapter<CustomerListAdapter.CustomerViewHolder>() {

    class CustomerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCustomerCoordinates: TextView = itemView.findViewById(R.id.tvCustomerCoordinates)
        val tvCustomerDemand: TextView = itemView.findViewById(R.id.tvCustomerDemand)
        val tvCustomerTime: TextView = itemView.findViewById(R.id.tvCustomerTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_customer, parent, false)
        return CustomerViewHolder(view)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        val customer = customerList[position]
        holder.tvCustomerCoordinates.text = "Koordinat: (${customer.xc}, ${customer.yc})"
        holder.tvCustomerDemand.text = "Talep: ${customer.demand}"
        holder.tvCustomerTime.text = "Zaman Penceresi: ${customer.ready_time} - ${customer.due_time} / Servis: ${customer.service_time} dk"
    }

    override fun getItemCount(): Int = customerList.size
}
