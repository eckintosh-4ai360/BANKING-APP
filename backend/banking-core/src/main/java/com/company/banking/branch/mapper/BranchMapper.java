package com.company.banking.branch.mapper;

import com.company.banking.branch.dto.BranchResponse;
import com.company.banking.branch.entity.Branch;
import org.mapstruct.Mapper;

@Mapper
public interface BranchMapper {

    BranchResponse toResponse(Branch branch);
}
