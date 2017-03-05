package org.jetbrains.ruby.runtime.signature.server;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.jetbrains.ruby.runtime.signature.RSignature;
import org.jetbrains.ruby.runtime.signature.RSignatureContractContainer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class SignatureServer {
    private static final Logger LOGGER = Logger.getLogger("SignatureServer");

    public static void main(String[] args) throws IOException {
        LOGGER.info("Starting server");

        RSignatureContractContainer mainContainer = new RSignatureContractContainer();

        Path file = Paths.get("callStatLog.txt");

        try (ServerSocket listener = new ServerSocket(7777)) {
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket socket = listener.accept();
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String currString;

                while ((currString = br.readLine()) != null) {
                    try {
                        if (currString.equals("break connection")) {
                            mainContainer.print(file);
                            break;
                        }

                        ServerResponseBean result = new Gson().fromJson(currString, ServerResponseBean.class);

                        if (result != null) {
                            RSignature currSignature = new RSignature(result);

                            if (result.method_name.equals("initialize") || result.call_info_mid.equals("send") || result.call_info_mid.equals("nil") || result.call_info_mid.equals(result.method_name)) {

                                mainContainer.addSignature(currSignature);
                            }

                        }

                    } catch (JsonParseException e) {
                        LOGGER.severe("!" + currString + "!\n" + e);
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}