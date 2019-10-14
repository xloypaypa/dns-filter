package online.xloypaypa.dns.relay.network.client;

import com.google.gson.JsonArray;
import coredns.dns.Dns;
import online.xloypaypa.dns.relay.config.ClientConfig;
import online.xloypaypa.dns.relay.config.Config;
import online.xloypaypa.dns.relay.network.client.util.DirectDnsClient;

import javax.net.ssl.SSLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class MultiDnsClient {

    private final JsonArray clients;
    private final ExecutorService executor;

    public MultiDnsClient(JsonArray clients, ExecutorService executor) {
        this.clients = clients;
        this.executor = executor;
    }

    public Dns.DnsPacket query(Dns.DnsPacket request) throws InterruptedException, SSLException {
        DirectDnsClient[] directDnsClients = generateDirectDnsClients();

        List<Future<Dns.DnsPacket>> futures = Arrays.stream(directDnsClients)
                .map(directDnsClient -> executor.submit(() -> directDnsClient.query(request)))
                .collect(Collectors.toList());

        while (futures.stream().allMatch(Future::isDone)) {
            Thread.sleep(50);
        }
        return Config.getConfig().getMergerConfig().getMerger().mergeResponds(request, clients, futures.stream().map(now -> {
            try {
                return now.get();
            } catch (InterruptedException | ExecutionException e) {
                return null;
            }
        }).collect(Collectors.toList()));
    }

    private DirectDnsClient[] generateDirectDnsClients() throws SSLException {
        DirectDnsClient[] directDnsClients = new DirectDnsClient[clients.size()];
        for (int i = 0; i < clients.size(); i++) {
            directDnsClients[i] = new DirectDnsClient(new ClientConfig(clients.get(i).getAsJsonObject()));
        }
        return directDnsClients;
    }

}
