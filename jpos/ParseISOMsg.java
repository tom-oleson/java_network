import org.jpos.iso.packager.ISO87BPackager;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;


// run with:
// javac -cp .:jpos.jar ParseISOMsg.java
// java -cp .:jpos.jar ParseISOMsg 

public class ParseISOMsg {
    public static void main(String[] args) throws ISOException {
        String hexmsg = args[0];
        // convert hex string to byte array
        byte[] bmsg =ISOUtil.hex2byte(hexmsg);
        ISOMsg m = new ISOMsg();
        // set packager, change ISO87BPackager for the matching one.
        m.setPackager(new ISO87BPackager());
        //unpack the message using the packager
        m.unpack(bmsg);
        //dump the message to standar output
        m.dump(System.out, "");
    }
}