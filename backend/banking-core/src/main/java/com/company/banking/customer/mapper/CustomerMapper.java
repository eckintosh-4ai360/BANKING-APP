package com.company.banking.customer.mapper;

import com.company.banking.customer.dto.AddressResponse;
import com.company.banking.customer.dto.BusinessProfileResponse;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.dto.IdentificationResponse;
import com.company.banking.customer.dto.IdentificationTypeResponse;
import com.company.banking.customer.dto.IndividualProfileResponse;
import com.company.banking.customer.dto.NextOfKinResponse;
import com.company.banking.customer.dto.RelatedPartyResponse;
import com.company.banking.customer.entity.BusinessProfile;
import com.company.banking.customer.entity.Customer;
import com.company.banking.customer.entity.CustomerAddress;
import com.company.banking.customer.entity.CustomerIdentification;
import com.company.banking.customer.entity.IdentificationType;
import com.company.banking.customer.entity.IndividualProfile;
import com.company.banking.customer.entity.NextOfKin;
import com.company.banking.customer.entity.RelatedParty;
import org.mapstruct.Mapper;

@Mapper
public interface CustomerMapper {

    CustomerSummary toSummary(Customer customer);

    IndividualProfileResponse toResponse(IndividualProfile profile);

    BusinessProfileResponse toResponse(BusinessProfile profile);

    AddressResponse toResponse(CustomerAddress address);

    IdentificationResponse toResponse(CustomerIdentification identification);

    NextOfKinResponse toResponse(NextOfKin nextOfKin);

    RelatedPartyResponse toResponse(RelatedParty relatedParty);

    IdentificationTypeResponse toResponse(IdentificationType identificationType);
}
