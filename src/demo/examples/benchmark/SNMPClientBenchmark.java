package demo.examples.benchmark;

import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class SNMPClientBenchmark {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java SNMPClientBenchmark <IP> <Port> <Community>");
            return;
        }

        String ip = args[0];
        int port = Integer.parseInt(args[1]);
        String community = args[2];

        TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
        transport.listen();

        Snmp snmp = new Snmp(transport);

        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(community));
        target.setVersion(SnmpConstants.version2c);
        target.setAddress(new UdpAddress(ip + "/" + port));
        target.setRetries(1);
        target.setTimeout(1500);

        OID testOID = new OID("1.3.6.1.2.1.1.3.0"); // sysUpTime

        PDU pdu = new PDU();
        pdu.setType(PDU.GET);
        pdu.add(new VariableBinding(testOID));

        int numRequests = 1000;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numRequests; i++) {
            ResponseEvent responseEvent = snmp.send(pdu, target);
            if (responseEvent.getResponse() == null) {
                System.err.println("No response for request " + i);
            }
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        System.out.println("Total time: " + totalTime + " ms");
        System.out.println("Average time per request: " + (double) totalTime / numRequests + " ms");
        System.out.println("Throughput: " + (numRequests * 1000.0 / totalTime) + " requests/sec");

        snmp.close();
    }
}
