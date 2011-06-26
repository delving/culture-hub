package eu.delving.sip;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * This class handles the creation and checking from the key which allows people
 * to access the services REST API.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class AccessKey {

    private String servicesPassword;

    public void setServicesPassword(String servicesPassword) {
        this.servicesPassword = servicesPassword;
    }

    public boolean checkKey(String key) {
        return servicesPassword != null && checkKey(key, servicesPassword);
    }

    public String createKey(String prefix) {
        return createKey(prefix, servicesPassword);
    }

    static boolean checkKey(String key, String servicesPassword) {
        if (key.length() <= 22) {
            return false;
        }
        key = key.toUpperCase();
        int dash = key.indexOf('-');
        if (dash < 0) {
            return false;
        }
        String userToken = key.substring(0, dash);
        String hash = key.substring(dash + 1);
        String calculatedHash = hash(userToken, servicesPassword);
        return calculatedHash.equals(hash);
    }

    static String hash(String userToken, String servicesPassword) {
        String hash = encodePassword(userToken.toUpperCase() + " hashed against " + servicesPassword);
        return hash.substring(0, 20).toUpperCase();
    }

    static String createKey(String userToken, String servicesPassword) {
        return userToken.toUpperCase() + "-" + hash(userToken, servicesPassword);
    }

    private static String encodePassword(String password) {
        MessageDigest messageDigest = getMessageDigest();
        byte[] digest;
        try {
            digest = messageDigest.digest(password.getBytes("UTF-8"));
        }
        catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not supported!");
        }
        return new String(encode(messageDigest.digest(digest)));
    }

    private static MessageDigest getMessageDigest() throws IllegalArgumentException {
        try {
            return MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("No such algorithm [SHA-1]");
        }
    }

    private static final char[] HEX = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    private static char[] encode(byte[] bytes) {
        final int nBytes = bytes.length;
        char[] result = new char[2*nBytes];
        int j = 0;
        for (int i=0; i < nBytes; i++) {
            // Char for top 4 bits
            result[j++] = HEX[(0xF0 & bytes[i]) >>> 4 ];
            // Bottom 4
            result[j++] = HEX[(0x0F & bytes[i])];
        }
        return result;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: <user-token> <services-password>");
            System.exit(0);
        }
        String userToken = args[0];
        String servicesPassword = args[1];
        System.out.println("Key is " + createKey(userToken, servicesPassword));
    }

}
