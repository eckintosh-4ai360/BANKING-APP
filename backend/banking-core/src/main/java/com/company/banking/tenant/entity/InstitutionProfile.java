package com.company.banking.tenant.entity;

import com.company.banking.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Institution-managed contact details and white-label branding (1:1 with the tenant).
 */
@Getter
@Entity
@Table(name = "institution_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstitutionProfile extends AuditableEntity {

    public static final String DEFAULT_PRIMARY_COLOR = "#0B3B60";
    public static final String DEFAULT_SECONDARY_COLOR = "#C8A24A";

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "support_email", length = 254)
    private String supportEmail;

    @Column(name = "support_phone", length = 20)
    private String supportPhone;

    @Column(name = "website_url", length = 255)
    private String websiteUrl;

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "digital_address", length = 20)
    private String digitalAddress;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor;

    @Column(name = "secondary_color", nullable = false, length = 7)
    private String secondaryColor;

    @Column(name = "sms_sender_id", length = 11)
    private String smsSenderId;

    @Column(name = "email_sender_name", length = 100)
    private String emailSenderName;

    @Column(name = "email_sender_address", length = 254)
    private String emailSenderAddress;

    public InstitutionProfile(UUID tenantId, String contactEmail, String contactPhone) {
        this.tenantId = tenantId;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.supportEmail = contactEmail;
        this.supportPhone = contactPhone;
        this.primaryColor = DEFAULT_PRIMARY_COLOR;
        this.secondaryColor = DEFAULT_SECONDARY_COLOR;
    }

    public void updateContact(String contactEmail, String contactPhone, String supportEmail, String supportPhone,
                              String websiteUrl, String addressLine1, String addressLine2, String city,
                              String region, String digitalAddress) {
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.supportEmail = supportEmail;
        this.supportPhone = supportPhone;
        this.websiteUrl = websiteUrl;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.region = region;
        this.digitalAddress = digitalAddress;
    }

    public void updateBranding(String logoUrl, String primaryColor, String secondaryColor, String smsSenderId,
                               String emailSenderName, String emailSenderAddress) {
        this.logoUrl = logoUrl;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.smsSenderId = smsSenderId;
        this.emailSenderName = emailSenderName;
        this.emailSenderAddress = emailSenderAddress;
    }
}
