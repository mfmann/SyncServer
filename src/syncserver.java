import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import filesync.SynchronisedFile;


public class syncserver {
	
	public static void main(String[] args){
		
		//Gets correct arguments from the command line
		ServerCommandLine line = new ServerCommandLine();
        CmdLineParser parser = new CmdLineParser(line);
        try {
        	parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.out.println(e.getMessage());
            parser.printUsage(System.err);
            System.exit(-1);
        }
			
		String fileName = line.filename();	
		int port = line.serverport();
		
		//Socket is created at server with specified or default port number
		DatagramSocket socket = null;
		try{
			socket = new DatagramSocket(port);
		}catch(SocketException e){
			e.printStackTrace();
			System.exit(-1);
		}
		
		System.out.println("Waiting for negotiation message...");
		//Server attempts to receive negotiation message
		byte[] receiveBuffer = new byte[1024];
		DatagramPacket received = new DatagramPacket(receiveBuffer, receiveBuffer.length);
		try {
			socket.receive(received);
		} catch (IOException e) {
			e.printStackTrace();
		}
				
		String stringMessage = new String(received.getData());
		stringMessage = stringMessage.replace("\n","");
		
		System.out.println(stringMessage);
		
		//Converts string received into type Message
		MessageFactory factory = new MessageFactory();
		Message negotiation = factory.FromJSON(stringMessage);
		
		//System exits if server does not receive negotiation message
		if(!negotiation.type().equals("negotiation")){
			System.out.println("Negotiation message required.");
			System.exit(-1);
		}
		
		//Sets blocksize and direction as specified in the negotiation message received
		long blocksize = ((NegotiationMessage) negotiation).blocksize();
		String direction = ((NegotiationMessage) negotiation).direction();
		
		//New synchronised file is created based on the file and blocksize
		SynchronisedFile file = null;
		try{
			file = new SynchronisedFile(fileName, (int) blocksize);		
		}catch(IOException e){
			e.printStackTrace();
			System.exit(-1);
		}
		
		InetAddress clientAddress = received.getAddress();
		int clientPort = received.getPort();
		
		//A new set of instructions for the server/client to follow are created based on the arguments received
		SyncInstructions instructions = new SyncInstructions(socket, file, blocksize, clientPort, clientAddress);	
		
		if(direction.equals("pull")){
			instructions.SyncAsClient();
		}else if(direction.equals("push")){
			instructions.SyncAsServer();
		}				
	}
}
