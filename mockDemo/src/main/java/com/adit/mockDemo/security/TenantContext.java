package com.adit.mockDemo.security;

import com.adit.mockDemo.entity.Organization;
import com.adit.mockDemo.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
public class TenantContext {

    private static final String ORG_ATTRIBUTE = "currentOrganization";

    public Organization getCurrentOrganization() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            throw new ValidationException("No request context available");
        }

        HttpServletRequest request = attributes.getRequest();
        Organization org = (Organization) request.getAttribute(ORG_ATTRIBUTE);

        if (org == null) {
            throw new ValidationException("No organization found in request. Ensure API key is provided.");
        }

        return org;
    }
}