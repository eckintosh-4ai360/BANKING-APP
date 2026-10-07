package com.company.banking.kyc.mapper;

import com.company.banking.kyc.dto.KycCheckResponse;
import com.company.banking.kyc.dto.KycTierResponse;
import com.company.banking.kyc.entity.KycCheck;
import com.company.banking.kyc.entity.KycTier;
import org.mapstruct.Mapper;

@Mapper
public interface KycMapper {

    KycTierResponse toResponse(KycTier tier);

    KycCheckResponse toResponse(KycCheck check);
}
