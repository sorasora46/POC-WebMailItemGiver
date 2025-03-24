package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import br.net.gmj.nobookie.LTItemMail.LTItemMail;
import br.net.gmj.nobookie.LTItemMail.LTItemMailAPI;
import br.net.gmj.nobookie.LTItemMail.entity.LTPlayer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class WebMailItemGiver extends JavaPlugin {
    private static final int PORT = 7799;
    private Server server;
    private static final Gson gson = new Gson();

    @Override
    public void onEnable() {
        startWebServer();
        getLogger().info("✅ WebMailItemGiver has been enabled!");
    }

    @Override
    public void onDisable() {
        if (server != null) {
            try {
                server.stop();
                getLogger().info("❌ WebMailItemGiver has been disabled!");
            } catch (Exception e) {
                getLogger().severe("Error stopping Jetty: " + e.getMessage());
            }
        }
    }

    private void startWebServer() {
        server = new Server(PORT);
        server.setHandler(new RequestHandler());

        new Thread(() -> {
            try {
                server.start();
                getLogger().info("🌍 Web server started on port " + PORT);
                server.join();
            } catch (Exception e) {
                getLogger().severe("Failed to start web server: " + e.getMessage());
            }
        }).start();
    }

    static class RequestHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                sendResponse(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "{\"error\":\"Only POST allowed\"}");
                baseRequest.setHandled(true);
                return;
            }

            // Read JSON request body
            StringBuilder requestBody = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    requestBody.append(line);
                }
            }

            JsonObject json;
            try {
                json = gson.fromJson(requestBody.toString(), JsonObject.class);
            } catch (Exception e) {
                sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, "{\"error\":\"Invalid JSON\"}");
                baseRequest.setHandled(true);
                return;
            }

            if (!json.has("player") || !json.has("item") || !json.has("amount")) {
                sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, "{\"error\":\"Missing fields\"}");
                baseRequest.setHandled(true);
                return;
            }

            String playerName = json.get("player").getAsString();
            String itemName = json.get("item").getAsString();
            int amount = json.get("amount").getAsInt();

            // Validate input
            if (amount <= 0) {
                sendResponse(response, HttpServletResponse.SC_BAD_REQUEST, "{\"error\":\"Amount must be greater than 0\"}");
                baseRequest.setHandled(true);
                return;
            }

            // Run on Bukkit main thread to avoid threading issues
            Bukkit.getScheduler().runTask(WebMailItemGiver.getPlugin(WebMailItemGiver.class), () -> {
                LTPlayer player = LTPlayer.fromName(playerName);
                if (player == null) {
                    getPlugin(WebMailItemGiver.class).getLogger().warning("Player not found: " + playerName);
                } else {
                    ItemStack itemStack = new ItemStack(Material.matchMaterial(itemName), amount);
                    if (itemStack.getType().isAir()) {
                        getPlugin(WebMailItemGiver.class).getLogger().warning("Invalid item: " + itemName);
                    } else {
                        getPlugin(WebMailItemGiver.class).getLogger().info("Is LTItemMail instance null: " + String.valueOf(LTItemMail.getInstance() == null));
                        var items = new LinkedList<ItemStack>();
                        items.add(itemStack);
                        var label = "Thank you!";
                        //Plugin mailAPI = Bukkit.getPluginManager().getPlugin("LTItemMailAPI");
                        LTItemMailAPI.sendSpecialMail(player, items, label);
//                        player.getInventory().addItem(itemStack);
                        getPlugin(WebMailItemGiver.class).getLogger().info("Gave " + amount + " " + itemName + " to " + playerName);
                    }
                }
            });

            sendResponse(response, HttpServletResponse.SC_OK, "{\"message\":\"Item sent\"}");
            baseRequest.setHandled(true);
        }

        private void sendResponse(HttpServletResponse response, int status, String json) throws IOException {
            response.setStatus(status);
            response.setContentType("application/json; charset=UTF-8");
            try (OutputStream os = response.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        }
    }
}
