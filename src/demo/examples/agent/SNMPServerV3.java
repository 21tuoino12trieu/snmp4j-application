package demo.examples.agent;

import demo.Constants;
import org.snmp4j.*;
import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.agent.mo.MOScalar;
import org.snmp4j.mp.*;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class SNMPServerV3 {

    private Snmp snmp;

    public SNMPServerV3() throws Exception {
        MessageDispatcher dispatcher = new MessageDispatcherImpl();

        OctetString localEngineID = new OctetString("1234567890123456");
        SecurityProtocols.getInstance().addDefaultProtocols();

        USM usm = new USM(SecurityProtocols.getInstance(), localEngineID, 0);
        usm.setEngineDiscoveryEnabled(true);
        SecurityModels.getInstance().addSecurityModel(usm);

        SecurityProtocols.getInstance().addPrivacyProtocol(new Priv3DES());
        SecurityProtocols.getInstance().addAuthenticationProtocol(new AuthMD5());

        MPv3 mPv3 = new MPv3(usm);
        dispatcher.addMessageProcessingModel(mPv3);

        TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping(new UdpAddress("127.0.0.1/161"));
        transport.listen();

        System.out.println("SNMP server is running on: " + transport.getListenAddress());

        snmp = new Snmp(dispatcher, transport);

        addSNMPv3User(usm, "snmpv3user", AuthMD5.ID, "authpassword", Priv3DES.ID, "privpassword");

        registerManagedObjects();

        System.out.println("SNMP server is initialized.");
    }

    private void addSNMPv3User(USM usm, String securityName, OID authProtocol, String authToken, OID privProtocol, String privToken) {
        UsmUser user = new UsmUser(
                new OctetString(securityName),
                authProtocol,
                new OctetString(authToken),
                privProtocol,
                new OctetString(privToken)
        );

        usm.addUser(new OctetString(securityName), user);
        System.out.println("SNMPv3 user added: " + securityName);
    }

    private void registerManagedObjects() {
        try {
            MOScalar<OctetString> hostname = new MOScalar<>(
                    Constants.OID_HOSTNAME,
                    MOAccessImpl.ACCESS_READ_ONLY,
                    new OctetString("Demo SNMP Server")
            );

            MOScalar<TimeTicks> uptime = new MOScalar<>(
                    Constants.OID_UPTIME,
                    MOAccessImpl.ACCESS_READ_ONLY,
                    new TimeTicks((System.currentTimeMillis() / 10) & 0xFFFFFFFFL)
            );

            MOScalar<OctetString> contact = new MOScalar<>(
                    new OID("1.3.6.1.2.1.1.4.0"),
                    MOAccessImpl.ACCESS_READ_ONLY,
                    new OctetString("admin@example.com")
            );

            System.out.println("OID_HOSTNAME registered: " + Constants.OID_HOSTNAME);
            System.out.println("OID_UPTIME registered: " + Constants.OID_UPTIME);
            System.out.println("OID_CONTACT registered: 1.3.6.1.2.1.1.4.0");

        } catch (Exception e) {
            System.err.println("Error while registering OIDs: " + e.getMessage());
        }
    }

    public void start() throws Exception {
        snmp.addCommandResponder(new CommandResponder() {
            @Override
            public void processPdu(CommandResponderEvent event) {
                PDU request = event.getPDU();
                if (request != null && request.getType() == PDU.GET) {
                    System.out.println("Received SNMP GET request from: " + event.getPeerAddress());
                    request.getVariableBindings().forEach(vb -> System.out.println("Requested OID: " + vb.getOid()));

                    PDU response = handleGetRequest(request);

                    try {
                        event.getMessageDispatcher().returnResponsePdu(
                                event.getMessageProcessingModel(),
                                event.getSecurityModel(),
                                event.getSecurityName(),
                                event.getSecurityLevel(),
                                response,
                                event.getMaxSizeResponsePDU(),
                                event.getStateReference(),
                                null
                        );
                        System.out.println("Response sent successfully: " + response);
                    } catch (Exception e) {
                        System.err.println("Error while sending response: " + e.getMessage());
                    }
                } else {
                    System.out.println("Received null or unsupported request type.");
                }
            }
        });

        System.out.println("SNMP server is now listening for requests...");
        Thread.sleep(Long.MAX_VALUE);
    }

    private PDU handleGetRequest(PDU request) {
        PDU response = new PDU();
        response.setType(PDU.RESPONSE);

        response.setRequestID(request.getRequestID());

        for (VariableBinding vb : request.getVariableBindings()) {
            OID oid = vb.getOid();
            Variable variable;

            if (oid.equals(Constants.OID_HOSTNAME)) {
                variable = new OctetString("Demo SNMP Server");
            } else if (oid.equals(Constants.OID_UPTIME)) {
                variable = new TimeTicks((System.currentTimeMillis() / 10) & 0xFFFFFFFFL);
            } else if (oid.equals(new OID("1.3.6.1.2.1.1.4.0"))) {
                variable = new OctetString("admin@example.com");
            } else {
                variable = new Null();
            }

            response.add(new VariableBinding(oid, variable));
        }

        return response;
    }

    public static void main(String[] args) throws Exception {
        SNMPServerV3 server = new SNMPServerV3();
        server.start();
    }
}
