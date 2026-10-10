package com.company.banking.channel.repository;

import com.company.banking.channel.entity.ChannelSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelSettingsRepository extends JpaRepository<ChannelSettings, UUID> {
}
