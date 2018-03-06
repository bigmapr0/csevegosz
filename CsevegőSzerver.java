/*
 * CsevegőSzerver.java
 *
 * DIGIT 2005, Javat tanítok
 * Bátfai Norbert, nbatfai@inf.unideb.hu
 *
 */
/**
 * Egy csevegőt kiszolgáló szál.
 *
 * @author Bátfai Norbert, nbatfai@inf.unideb.hu
 * @version 0.0.1
 */
class Kiszolgáló implements Runnable {
    /** A csevegő szerver, aki ezt a kiszolgáló szálat készítette és
     * használja. */
    CsevegőSzerver szerver;
    /** A kiszolgálást ezen a TCP/IP kapcsolatot absztraháló
     * objektumon kersztül végezzük éppen. */
    java.net.Socket socket;
    /** A kapcsolat feletti kimenő csatorna (látnia kell a többi
     * kiszolgálást végző szálnak is) */
    java.io.PrintWriter kimenőCsatorna;
    /** Dolgozik éppen vagy sem az objektum? */
    boolean kiszolgál = false;
    /**
     * A {@code Kiszolgáló} objektumot felépítő konstruktor.
     *
     * @param   szerver a csevegő szálakat összefogó szerver.
     */
    public Kiszolgáló(CsevegőSzerver szerver) {
        
        this.szerver = szerver;
        // Készítjük a szálat és indítjuk, az ennek
        // megfelelő run() módszerben pedig azonnal
        // altatjuk majd az indított szálat.
        new Thread(this).start();
        
    }
    /**
     * A szál kiszolgálását indító függvény.
     *
     * @param   socket  kapcsolat a TCP/IP-s klienssel.
     */
    public synchronized void kiszolgál(java.net.Socket socket) {
        this.socket = socket;
        kiszolgál = true;
        // A run()-ban alvó szálat ébresztjük, az ott a
        // wait()-nál várakozó végrehajtási szál elindul.
        notify();
    }
    /**
     * Üzenet a kliensnek.
     *
     * @param üzenet amit el köldünk a kliensnek.
     */
    public void üzenet(String üzenet) {
        //kimenőCsatorna.println(üzenet + szerver.getCsevego());
	kimenőCsatorna.println(üzenet);
        kimenőCsatorna.flush();
    }
    /**
     * A kliensek kiszolgálását végző szál.
     */
    public synchronized void run() {
        for(;;) {
            // Tétlenül várakozik addig, amig nincs kliens
            while(!kiszolgál)
                try{
                    // a várakozásból a notify() hívás ébreszti majd fel.
                    wait();
                } catch(InterruptedException e) {}
            // Kimenő és bejövő csatorna objektumokat elkészítjük.
            try {
                java.io.BufferedReader bejövőCsatorna =
                        new java.io.BufferedReader(
                        new java.io.InputStreamReader(socket.getInputStream()));
                kimenőCsatorna =
                        new java.io.PrintWriter(socket.getOutputStream());
                // Addig olvasunk, amig ki nem lép a kliens
                // a vege parancs begépelésével.
                String sor = bejövőCsatorna.readLine();
                do {
                    if("vege".equals(sor))
                        break;
                    // Visszahívjuk a szervert, hogy a klienstől
                    // beolvasott üzenetet eljutassa minden csevegőhöz:
                    szerver.mindenkihez(sor);
                    
                } while((sor = bejövőCsatorna.readLine()) != null);
                
            } catch(java.io.IOException ioE) {
                
                ioE.printStackTrace();
                
            } finally {
                
                try{
                    socket.close();
                    szerver.kiszáll(this);
                    kiszolgál = false;
                } catch(java.io.IOException ioE) {}
                
            }
        }
    }
}
/**
 * A csevegő szerver.
 *
 * @author Bátfai Norbert, nbatfai@inf.unideb.hu
 * @version 0.0.1
 */
public class CsevegőSzerver {
    /** Maximum hány csevegő fér be. */
    public static final int MAX_CSEVEGŐ = 20;
    /** Itt tartjuk az éppen csevegést lebonyolító szálakat. */
    private java.util.List<Kiszolgáló> csevegők;
    /** Itt tartjuk az éppen csevegést nem bonyolító szálakat. */
    private java.util.List<Kiszolgáló> nemCsevegők;
    /** A <code>CsevegőSzerver</code> objektumot felépítő konstruktor. */
    public CsevegőSzerver() {
        nemCsevegők = new java.util.ArrayList<Kiszolgáló>();
        csevegők = new java.util.ArrayList<Kiszolgáló>();
    // Csevegő szálak elkészítése
        for(int i=0; i<MAX_CSEVEGŐ; ++i)
            nemCsevegők.add(new Kiszolgáló(this));
        // Szerver indítása
        try {
            java.net.ServerSocket serverSocket =
                    new java.net.ServerSocket(2006);
            
            while(true) {
                java.net.Socket socket = serverSocket.accept();
                Kiszolgáló szál = beszáll();
                if(szál == null) {
                    // Ha betelt a csevegő szoba, akkor egy
                    // rövid tájékoztató üzenet a kliensnek:
                    java.io.PrintWriter kimenőCsatorna =
                            new java.io.PrintWriter(socket.getOutputStream());
                    kimenőCsatorna.println("A csevegő szoba tele van!");
                    socket.close();
                } else // Ha van szabad csevegő szál, akkor
                    // azzal elkezdődik a csevegő kliens kiszolgálása.
                    szál.kiszolgál(socket);
            }
            
        } catch(java.io.IOException ioE) {
            ioE.printStackTrace();
        }

    }
    /**
     * Egy szálat átteszünk a nem csevegők közül a csevegőkbe.
     * Az alábbi három, az adott szerverhez tartozó nem csevegőket
     * a csevegőket kezelő módszer közül mindig csak egy futhat, ezt
     * az objektum szintű szinkronizált módszerek használatával
     * biztosítottuk.
     */
    public int getCsevego() {

    return csevegők.size();
    }

    public synchronized Kiszolgáló beszáll() {
        
        if(!nemCsevegők.isEmpty()) {
            Kiszolgáló kiszolgáló = nemCsevegők.remove(0);
            csevegők.add(kiszolgáló);
            return kiszolgáló;
        }
        return null;
    }
    /**
     * Egy szál aktuális működése végének leadminisztrálása.
     *
     * @param csevegő   szál, aki éppen befejezte működését.
     */
    public synchronized void kiszáll(Kiszolgáló csevegő) {
        csevegők.remove(csevegő);
        nemCsevegők.add(csevegő);
    }
    /**
     * Üzenet küldése a csevegő klienseknek.
     *
     * @param üzenet    amit minden kliensnek el kell küldeni.
     */
    public synchronized void mindenkihez(String üzenet) {
        
        for(Kiszolgáló csevegő: csevegők)
            csevegő.üzenet(üzenet);
        
    }
    /** A csevegő szerver elindítása. */
    public static void main(String [] args) {
        new CsevegőSzerver();
    }
}
