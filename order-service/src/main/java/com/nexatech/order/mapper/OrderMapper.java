package com.nexatech.order.mapper;

import com.nexatech.order.dto.OrderResponse;
import com.nexatech.order.model.Order;
import com.nexatech.order.model.OrderItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderResponse toResponse(Order order);

    OrderResponse.OrderItemResponse toItemResponse(OrderItem item);
}
