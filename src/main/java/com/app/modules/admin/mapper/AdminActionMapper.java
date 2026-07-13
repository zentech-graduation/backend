package com.app.modules.admin.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.dto.response.AdminActionSummaryResponse;
import com.app.modules.admin.entity.AdminAction;

/** Maps {@link AdminAction} audit entities to admin-layer response DTOs. */
@Mapper(componentModel = "spring")
public interface AdminActionMapper {

    AdminActionResponse toResponse(AdminAction action);

    AdminActionSummaryResponse toSummaryResponse(AdminAction action);

    List<AdminActionSummaryResponse> toSummaryResponseList(List<AdminAction> actions);
}
