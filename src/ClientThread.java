import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import filesync.CopyBlockInstruction;
import filesync.Instruction;
import filesync.NewBlockInstruction;
import filesync.SynchronisedFile;


public class ClientThread implements Runnable{
	
	SynchronisedFile file;
	DatagramSocket socket;
	long clientCounter;
	InetAddress remoteAddress;
	int remotePort;
	
	ClientThread(SynchronisedFile file, DatagramSocket socket, InetAddress remoteAddress, int remotePort){
		this.file=file;
		this.socket=socket;
		this.clientCounter = 1;
		this.remoteAddress = remoteAddress;
		this.remotePort = remotePort;
	}
	
	public void run(){
		
		Instruction nextInstruction;
		Instruction currentInstruction;
		Boolean sent;
		MessageFactory factory = new MessageFactory();

		while((nextInstruction=file.NextInstruction())!= null){
			
			//Resets counter to 1 if receives a "StartUpdate" instruction
			if(nextInstruction.Type().equals("StartUpdate")){
				clientCounter = 1;
			}	
			
			currentInstruction=nextInstruction;
			sent = false;
			
			//Attempts to send a message containing instructions until an "ack" message is received
			while(sent==false){
			
				 InstMessage toServer = new InstMessage(currentInstruction, clientCounter);	 
				 byte[] sendBuffer = toServer.ToJSON().getBytes();
				 DatagramPacket toSend = new DatagramPacket(sendBuffer, sendBuffer.length, remoteAddress, remotePort);
				
				 //Sets socket to timeout after 10 seconds
				try {
					socket.setSoTimeout(10000);
				} catch (SocketException e1) {
					e1.printStackTrace();
				}	
				
				byte[] replyBuffer = new byte[1024];	 
				DatagramPacket reply = new DatagramPacket(replyBuffer, replyBuffer.length);

				boolean received = false;
				//Message will be resent if client does not receive a reply from the server in 10 seconds
				while(received == false){			
					try {
						socket.send(toSend);
					} catch (IOException e) {
						e.printStackTrace();
						System.exit(-1);
					} 
				
					try {
						socket.receive(reply);
						received = true;
					//If socket timesout, message has to be resent	
					}catch (SocketTimeoutException e){
						received = false;			
					}catch (IOException e) {
							e.printStackTrace();
							System.exit(-1);
					}
				}
				 
				String stringReply = new String (reply.getData());
				 
				CounterMessage msgFromServer = (CounterMessage) factory.FromJSON(stringReply);
				String type = msgFromServer.type();
				long serverCounter = msgFromServer.counter();
				
				//Next instruction is sent in a message if an "ack" is received
				 if(type.equals("ack") && clientCounter==serverCounter){
					 sent = true;
					 clientCounter++;	
				//Next instruction is sent if the server already received this message
				 }else if(type.equals("expecting") && serverCounter==clientCounter+1){
					 sent = true;
				//Instruction is upgraded from a "NewBlock" to a "CopyBlock" and resent
				 }else if(type.equals("exception")){
					 sent = false;
					 currentInstruction=new NewBlockInstruction((CopyBlockInstruction)currentInstruction);
				 }	
			 }
		}
	}
}
