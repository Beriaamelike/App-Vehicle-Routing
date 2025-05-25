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
        val tvCustomerIdProductId: TextView = itemView.findViewById(R.id.tvCustomerIdProductId)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_customer, parent, false)
        return CustomerViewHolder(view)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        val customer = customerList[position]
        holder.tvCustomerIdProductId.text = "Müşteri ID: ${customer.customer_id} / Ürün ID: ${customer.product_id}"
        holder.tvCustomerCoordinates.text = "Koordinat: (${customer.xc}, ${customer.yc})"
        holder.tvCustomerDemand.text = "Ağırlık: ${customer.demand} kg"
        holder.tvCustomerTime.text = "Zaman Penceresi: ${customer.ready_time} dk - ${customer.due_time} dk / Servis: ${customer.service_time} dk"
    }

    override fun getItemCount(): Int = customerList.size
}
