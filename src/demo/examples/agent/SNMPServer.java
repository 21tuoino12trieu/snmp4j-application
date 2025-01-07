package demo.examples.agent;

import demo.Constants;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageDispatcher;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.agent.mo.MOScalar;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class SNMPServer {

    private Snmp snmp;

    public SNMPServer() throws Exception {
        // Tạo dispatcher để xử lý các request
        MessageDispatcher dispatcher = new MessageDispatcherImpl();
        dispatcher.addMessageProcessingModel(new MPv1());
        dispatcher.addMessageProcessingModel(new MPv2c());

        TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping(new UdpAddress("127.0.0.1/161"));
        transport.listen();

        System.out.println("SNMP server is running on: " + transport.getListenAddress());

        snmp = new Snmp(dispatcher, transport);

        registerManagedObjects();

        System.out.println("SNMP server is initialized.");
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
                    new TimeTicks((System.currentTimeMillis() / 10) & 0xFFFFFFFFL) // Chuyển giá trị thành unsigned 32-bit
            );

            System.out.println("OID_HOSTNAME registered: " + Constants.OID_HOSTNAME);
            System.out.println("OID_UPTIME registered: " + Constants.OID_UPTIME);

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
                    request.getVariableBindings().forEach(vb ->System.out.println("Requested OID: " + vb.getOid())
                    );

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
            Variable variable = null;

            if (oid.equals(Constants.OID_HOSTNAME)) {
                variable = new OctetString("Demo SNMP Server");
            } else if (oid.equals(Constants.OID_UPTIME)) {
                variable = new TimeTicks((System.currentTimeMillis() / 10) & 0xFFFFFFFFL); // Chuyển thành unsigned 32-bit
            } else {
                variable = new Null(); // Không tìm thấy OID
            }

            response.add(new VariableBinding(oid, variable));
        }

        return response;
    }


    public static void main(String[] args) throws Exception {
        SNMPServer server = new SNMPServer();
        server.start();
    }
}