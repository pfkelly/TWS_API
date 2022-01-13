package samples.testbed;

import java.util.Set;

public class SecurityDefinitionOptionalParameter {
    private int reqId;
    private String symbol;
    private String exchange;
    private String multiplier;
    private Set<String> expirations;
    private Set<Double> strikes;

    public static final SecurityDefinitionOptionalParameter NO_OPTION = new SecurityDefinitionOptionalParameter(0,"","","0",null, null);

    public SecurityDefinitionOptionalParameter(int reqId, String symbol, String exchange, String multiplier, Set<String> expirations, Set<Double> strikes) {
        this.reqId = reqId;
        this.symbol = symbol;
        this.exchange = exchange;
        this.multiplier = multiplier;
        this.expirations = expirations;
        this.strikes = strikes;
    }

    public int getReqId() {
        return reqId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getExchange() {
        return exchange;
    }

    public String getMultiplier() {
        return multiplier;
    }

    public Set<String> getExpirations() {
        return expirations;
    }

    public Set<Double> getStrikes() {
        return strikes;
    }

    @Override
    public String toString() {
        return "SecurityDefinitionOptionalParameter{" +
                "reqId=" + reqId +
                ", symbol='" + symbol + '\'' +
                ", exchange='" + exchange + '\'' +
                ", multiplier='" + multiplier + '\'' +
                ", expirations=" + expirations +
                ", strikes=" + strikes +
                '}';
    }
}
