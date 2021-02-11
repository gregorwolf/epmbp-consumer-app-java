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
import com.auth0.jwt.exceptions.JWTDecodeException;
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
import com.sap.cloud.sdk.cloudplatform.connectivity.exception.DestinationAccessException;
import com.sap.cloud.sdk.cloudplatform.resilience.ResilienceConfiguration;
import com.sap.cloud.sdk.cloudplatform.resilience.ResilienceDecorator;
import com.sap.cloud.sdk.cloudplatform.resilience.ResilienceConfiguration.TimeLimiterConfiguration;
import com.sap.cloud.sdk.cloudplatform.security.AuthToken;
import com.sap.cloud.sdk.cloudplatform.security.AuthTokenAccessor;
import com.sap.cloud.sdk.cloudplatform.security.exception.AuthTokenAccessException;
import com.sap.cloud.sdk.s4hana.connectivity.DefaultErpHttpDestination;
import com.sap.cloud.sdk.s4hana.datamodel.odata.exception.NoSuchEntityFieldException;
import com.sap.cloud.sdk.cloudplatform.connectivity.ScpCfHttpDestination;

import cds.gen.catalogservice.*;
import de.linuxdozent.vdm.namespaces.zepmbpsrvedmx.EPMBusinessPartner;
import de.linuxdozent.vdm.services.DefaultZEPMBPSRVEdmxService;
import de.linuxdozent.vdm.services.ZEPMBPSRVEdmxService;

@Component
@ServiceName(CatalogService_.CDS_NAME)
public class CatalogService implements EventHandler {

	Logger logger = LoggerFactory.getLogger(CatalogService.class);

	private final static String destinationName = "S4H_CC";

	private final TimeLimiterConfiguration timeLimit = TimeLimiterConfiguration.of()
			.timeoutDuration(Duration.ofSeconds(10));

	private final ResilienceConfiguration resilienceConfiguration = ResilienceConfiguration.of(CatalogService.class)
			.timeLimiterConfiguration(timeLimit);
	private final ZEPMBPSRVEdmxService epmBPservice = new DefaultZEPMBPSRVEdmxService();

	private final Map<Object, Map<String, Object>> epmBPs = new HashMap<>();

	public static HttpDestination getHttpDestinationToOnPremSSO(String authToken, Logger logger) {
		logger.info("Start getHttpDestinationToOnPremSSO for destination: " + destinationName);
		Destination destination = DestinationAccessor.tryGetDestination(destinationName).get();

		String url = destination.get("URL", String.class).getOrNull();
		ScpCfHttpDestination.Builder builder = ScpCfHttpDestination.builder(destinationName, url);

		// set properties
		for (String propertyName : destination.getPropertyNames()) {
			builder.property(propertyName, destination.get(propertyName).getOrNull());
		}

		// add missing token (a workaround as of Cloud SDK 3.11, until fixed)
		try {
			logger.info("Add authToken as SAP-Connectivity-Authentication header");
			logger.debug("authToken: " + authToken);
			builder.header("SAP-Connectivity-Authentication", "Bearer " + authToken);			
		} catch (AuthTokenAccessException e) {
			logger.error("Token can't be accessed. This is expected when we run local.");		
		}
		// decorate optional S/4 destination properties, e.g. sap-client
		return builder.build().decorate(DefaultErpHttpDestination::new);
	}
	
	private List<EPMBusinessPartner> _readEPMBusinessPartner(String jwt) throws Exception {
		return ResilienceDecorator.executeCallable(() -> epmBPservice.getAllEPMBusinessPartner()
				.select(EPMBusinessPartner.BUSINESS_PARTNER_ID, EPMBusinessPartner.COMPANY)
				.execute(getHttpDestinationToOnPremSSO(jwt, logger)), resilienceConfiguration);
	}

	public List<EPMBusinessPartner> readEPMBusinessPartner(String jwt) throws Exception {
		try {
			DecodedJWT decodedJWT = JWT.decode(jwt);			
			AuthTokenAccessor.executeWithAuthToken(new AuthToken(decodedJWT), () -> {
				logger.info("Call readEPMBusinessPartner in Cloud Foundr");
				List<EPMBusinessPartner> EPMBusinessPartners = this._readEPMBusinessPartner(jwt);
				final int size = EPMBusinessPartners.size();
				logger.info("Number of EPMBusinessPartners: " + size);
				return EPMBusinessPartners;
			});
		} catch (JWTDecodeException e) {
			logger.info("Call readEPMBusinessPartner for local testing");
			return this._readEPMBusinessPartner(jwt);		
		}
		return null;
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
		 * // Maybe we have to try it with the CAP service consumption? CdsService
		 * service = context.getService(); CqnSelect query =
		 * Select.from("my.bookshop.EPMBusinessPartners")
		 * .columns("BpId","CompanyName","City","Street"); Result result =
		 * service.run(query);
		 */

		try {
			String jwt = AuthenticatedUserClaimProvider.INSTANCE.getUserClaim();
			logger.debug("JWT: " + jwt);
			if (context.getCqn().limit().isPresent()) {
				final CqnLimit limit = context.getCqn().limit().get();
				final CqnValue rows = limit.rows();
				System.out.println("rows:" + rows.toString());
			}
			System.out.println("Read EPMBusinessPartners:" + context.toString());
			try {
				final List<EPMBusinessPartner> EPMBusinessPartners = this.readEPMBusinessPartner(jwt);
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
						partner.setCity(epmBP.getCity());
						partner.setStreet(epmBP.getStreet());
						epmBPs.put(epmBP.getBusinessPartnerID(), partner);
					} catch (final NoSuchEntityFieldException e) {
						logger.error("Error occurred with the Query operation: " + e.getMessage());
						throw new ServiceException("An internal server error occurred", e);
					}
				}				
			} catch (final Exception e) {
				logger.error("Error occurred with the Query operation: " + e.getMessage());
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
		String userDetails = jwtToken.getClaim("given_name").asString() + " "
				+ jwtToken.getClaim("family_name").asString();
		context.setResult(userDetails);
	}

	@On(event = "getUserToken")
	public void getUserToken(GetUserTokenEventContext context) {
		String jwt = AuthenticatedUserClaimProvider.INSTANCE.getUserClaim();
		context.setResult(jwt);
	}

}
