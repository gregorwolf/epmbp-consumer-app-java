package de.linuxdozent.epmbp.handlers;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;

@EventName("getUserDetails")
public interface GetUserDetailsEventContext extends EventContext {
    // The return value
    void setResult(String userDetails);
    String getResult();
}