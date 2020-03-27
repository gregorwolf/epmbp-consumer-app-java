package de.linuxdozent.epmbp.handlers;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.sap.cds.Struct;
import com.sap.cds.feature.auth.AuthenticatedUserClaimProvider;
import com.sap.cds.ql.cqn.CqnLimit;
import com.sap.cds.ql.cqn.CqnValue;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CdsService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cloud.sdk.cloudplatform.connectivity.Destination;
import com.sap.cloud.sdk.cloudplatform.connectivity.DestinationAccessor;
import com.sap.cloud.sdk.cloudplatform.connectivity.HttpDestination;
import com.sap.cloud.sdk.cloudplatform.connectivity.PrincipalPropagationStrategy;
import com.sap.cloud.sdk.cloudplatform.connectivity.exception.DestinationAccessException;
import com.sap.cloud.sdk.cloudplatform.resilience.ResilienceConfiguration;
import com.sap.cloud.sdk.cloudplatform.resilience.ResilienceDecorator;
import com.sap.cloud.sdk.cloudplatform.resilience.ResilienceConfiguration.TimeLimiterConfiguration;
import com.sap.cloud.sdk.s4hana.connectivity.DefaultErpHttpDestination;
import com.sap.cloud.sdk.s4hana.datamodel.odata.exception.NoSuchEntityFieldException;

import cds.gen.catalogservice.*;
import de.linuxdozent.vdm.namespaces.zepmbpsrvedmx.EPMBusinessPartner;
import de.linuxdozent.vdm.services.DefaultZEPMBPSRVEdmxService;
import de.linuxdozent.vdm.services.ZEPMBPSRVEdmxService;

@Component
@ServiceName(CatalogService_.CDS_NAME)
public class CatalogService implements EventHandler {
	
	Logger logger = LoggerFactory.getLogger(CatalogService.class);
	
	private final static String destinationName = "NPL";
		
	private final TimeLimiterConfiguration timeLimit = TimeLimiterConfiguration.of()
            .timeoutDuration(Duration.ofSeconds(10));

    private final ResilienceConfiguration resilienceConfiguration =
            ResilienceConfiguration.of(CatalogService.class)
			.timeLimiterConfiguration(timeLimit);
	private final ZEPMBPSRVEdmxService epmBPservice = new DefaultZEPMBPSRVEdmxService();
	
	private final Map<Object, Map<String, Object>> epmBPs = new HashMap<>();

	public static HttpDestination getHttpDestinationToOnPremSSO() {
        PrincipalPropagationStrategy.setDefaultStrategy(PrincipalPropagationStrategy.COMPATIBILITY);
        Destination destination = DestinationAccessor.getDestination(destinationName);
        return destination.asHttp().decorate(DefaultErpHttpDestination::new);
	}

	@On(event = CdsService.EVENT_CREATE, entity = EPMBusinessPartners_.CDS_NAME)
	public void onEPMBusinessPartnersCreate(final CdsCreateEventContext context) {
		context.getCqn().entries().forEach(e -> epmBPs.put(e.get("BpId"), e));
		context.setResult(context.getCqn().entries());
	}

	@On(event = CdsService.EVENT_READ, entity = EPMBusinessPartners_.CDS_NAME)
	public void onEPMBusinessPartnersRead(final CdsReadEventContext context) {
		System.out.println("destinations: " + System.getenv("destinations"));
		/*
		// Maybe we have to try it with the CAP service consumption?
		CdsService service = context.getService();
		CqnSelect query = Select.from("my.bookshop.EPMBusinessPartners")
			.columns("BpId","CompanyName","City","Street");
		Result result = service.run(query);
		*/

		try {
			// Maybe needed to get the JWT: AuthenticatedUserClaimProvider.INSTANCE.getUserClaim()
            String jwt = AuthenticatedUserClaimProvider.INSTANCE.getUserClaim();
            System.out.println("JWT: " + jwt);
			if (context.getCqn().limit().isPresent()) {
				final CqnLimit limit = context.getCqn().limit().get();
				final CqnValue rows = limit.rows();
				System.out.println("rows:" + rows.toString());
			}
			System.out.println("Read EPMBusinessPartners:" + context.toString());

			try {
				final List<EPMBusinessPartner> EPMBusinessPartners =  ResilienceDecorator.executeCallable(
	                    () -> epmBPservice
							.getAllEPMBusinessPartner()
							.select(EPMBusinessPartner.BUSINESS_PARTNER_ID, EPMBusinessPartner.COMPANY)
							.execute(getHttpDestinationToOnPremSSO()),
							resilienceConfiguration);
				final int size = EPMBusinessPartners.size();
				logger.info("Number of EPMBusinessPartners: " + size);

				// How to convert List EPMBusinessPartners to Map epmBPs
				final Iterator epmBPiterator = EPMBusinessPartners.iterator();
				while (epmBPiterator.hasNext()) {
					final EPMBusinessPartner epmBP = (EPMBusinessPartner) epmBPiterator.next();
					try {
						EPMBusinessPartners partner = Struct.create(EPMBusinessPartners.class);
						partner.setBpId(epmBP.getBusinessPartnerID());
						partner.setCompanyName(epmBP.getCompany());
						epmBPs.put(epmBP.getBusinessPartnerID(), partner);
					} catch (final NoSuchEntityFieldException e) {
						logger.error("Error occurred with the Query operation: " + e.getMessage());
						throw new ServiceException("An internal server error occurred", e);
					}
				}
			} catch (final Exception e) {
				logger.error("Error occurred with the Query operation: " + e.getMessage(), e);
				throw new ServiceException("An internal server error occurred", e);
			}
			context.setResult(epmBPs.values());
		} catch (final DestinationAccessException e) {
			System.out.println("Message: " + e.getMessage());
			throw new ServiceException("An internal server error occurred", e);
		}	
		
	}

	@On(event = "getUserDetails")
    public void getUserDetails(GetUserDetailsEventContext context) {
		String jwt = AuthenticatedUserClaimProvider.INSTANCE.getUserClaim();
		DecodedJWT jwtToken = JWT.decode(jwt);
		String userDetails = 
			jwtToken.getClaim("given_name").asString() + " " +
			jwtToken.getClaim("family_name").asString();
		context.setResult(userDetails);
	}

	@On(event = "getUserToken")
    public void getUserToken(GetUserTokenEventContext context) {
		String jwt = AuthenticatedUserClaimProvider.INSTANCE.getUserClaim();
		context.setResult(jwt);
	}

}

