import org.jpos.iso.packager.GenericPackager;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;

import java.util.Scanner;


// run with:
// javac -cp .:jpos.jar ParseISOMsg.java
// java -cp .:jpos.jar ParseISOMsg 

public class ParseISOMsg {
    public static void main(String[] args) throws ISOException {

        // Create a scanner to wrap the standard input stream
        Scanner scanner = new Scanner(System.in);
        String hex_msg = scanner.nextLine();

        ISOMsg iso_msg = new ISOMsg();
        GenericPackager packager = 
            new GenericPackager(iso_msg.getClass().getClassLoader().getResourceAsStream("basic.xml"));

        // set the packager for the message
        iso_msg.setPackager(packager);

        // convert hex string to byte array
        byte[] msg_bytes =ISOUtil.hex2byte(hex_msg);

        //unpack the message using the packager
        iso_msg.unpack(msg_bytes);

        //dump the message to standard output
        iso_msg.dump(System.out, "");
    }
}
