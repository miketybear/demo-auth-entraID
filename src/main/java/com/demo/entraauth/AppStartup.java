package com.demo.entraauth;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/** Loads auth.properties once at startup and shares one EntraAuthService with all servlets. */
@WebListener
public class AppStartup implements ServletContextListener {

    static final String SERVICE_KEY = "entra.authService";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        AuthConfig config = AuthConfig.load();   // fails fast with a clear message if config is wrong
        event.getServletContext().setAttribute(SERVICE_KEY, new EntraAuthService(config));
        event.getServletContext().log("Entra ID demo ready. Tenant=" + config.tenantId
                + " Client=" + config.clientId + " RedirectUri=" + config.redirectUri);
    }

    static EntraAuthService service(ServletContext ctx) {
        return (EntraAuthService) ctx.getAttribute(SERVICE_KEY);
    }
}
