package com.app.modules.admin.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.modules.admin.dto.response.AdminStrikeResponse;
import com.app.modules.admin.dto.response.AdminViolationResponse;
import com.app.modules.admin.dto.response.AdminWarningResponse;
import com.app.modules.admin.dto.response.UserWarningResponse;
import com.app.modules.admin.entity.UserStrike;
import com.app.modules.admin.entity.UserWarning;

/** Maps discipline rows to the three shapes the API exposes them in. */
@Mapper(componentModel = "spring")
public interface UserDisciplineMapper {

    AdminWarningResponse toWarningResponse(UserWarning warning);

    /**
     * Narrows a warning to what the warned account may see.
     *
     * <p>Drops the issuing moderator's identity and the revocation columns: an account listing its
     * own warnings sees only unrevoked ones, so a revocation timestamp there would always be null.
     */
    UserWarningResponse toOwnWarningResponse(UserWarning warning);

    AdminStrikeResponse toStrikeResponse(UserStrike strike);

    @Mapping(target = "kind", constant = AdminViolationResponse.KIND_WARNING)
    @Mapping(target = "actorId", source = "issuedBy")
    @Mapping(target = "strikeNumber", ignore = true)
    AdminViolationResponse toViolation(UserWarning warning);

    @Mapping(target = "kind", constant = AdminViolationResponse.KIND_STRIKE)
    @Mapping(target = "actorId", source = "triggeredBy")
    @Mapping(target = "reasonKey", ignore = true)
    @Mapping(target = "note", ignore = true)
    AdminViolationResponse toViolation(UserStrike strike);
}
