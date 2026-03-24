package com.nexatech.order.mapper;

import com.nexatech.order.dto.ReturnResponse;
import com.nexatech.order.model.OrderReturn;
import com.nexatech.order.model.OrderReturnItem;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper chuyển đổi OrderReturn → ReturnResponse.
 */
@Mapper(componentModel = "spring")
public interface ReturnMapper {

    ReturnResponse toResponse(OrderReturn orderReturn);

    ReturnResponse.ReturnItemResponse toItemResponse(OrderReturnItem item);
}
