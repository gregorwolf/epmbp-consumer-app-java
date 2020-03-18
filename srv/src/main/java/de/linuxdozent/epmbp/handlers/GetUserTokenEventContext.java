package de.linuxdozent.epmbp.handlers;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;

@EventName("getUserToken")
public interface GetUserTokenEventContext extends EventContext {
    // The return value
    void setResult(String userToken);
    String getResult();
}