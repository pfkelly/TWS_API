package samples.testbed;

import com.ib.client.ContractDetails;
import com.ib.client.EWrapperMsgGenerator;
import com.ib.client.TickAttrib;

import java.text.SimpleDateFormat;
import java.util.*;

public class MyEWrapper extends EWrapperImpl{

    private int numberOfLoadedSymbols = 0;

    private Set<String> symbols;
    //TODO: Do these maps need to be thread safe, i.e. Hashtable
    private Map<String, Double> lastBidForStock = new HashMap<>();
    private Map<String, Double> lastAskForStock = new HashMap<>();
    private Map<String, Double> lastPriceForStock = new HashMap<>();
    private Map<String, StockAndOptionContracts> stockAndOptionContracts = new HashMap<>();
    private Map<Integer, String> stockContractIdToSymbol = new HashMap<>();
    private Map<Integer, String> securityDefOptionalParameterReqIdToSymbol = new HashMap<>();
    private Map<Integer, String> stockDataReqIdToSymbol = new HashMap<>();
    private Map<Integer, String> optionDataReqIdToSymbol = new HashMap<>();
    private Map<String, Double> symbolToBidImpliedVol = new HashMap<>();
    private Map<String, Double> symbolToAskImpliedVol = new HashMap<>();
    private Map<String, Double> symbolToLastImpliedVol = new HashMap<>();
    private Map<String, Double> symbolToModelImpliedVol = new HashMap<>();
    private boolean loadedPriceForAllStocks = false;

    public MyEWrapper(String symbolArg) {
        super();
        String[] splitSymbols = symbolArg.split(",");
        symbols = new HashSet(splitSymbols.length);
        for (String symbol: splitSymbols) {
            stockAndOptionContracts.put(symbol, null);
            symbols.add(symbol);
        }
    }
    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        System.out.println(EWrapperMsgGenerator.contractDetails(reqId, contractDetails));
        String symbol = contractDetails.contract().symbol();
        stockContractIdToSymbol.put(contractDetails.conid(), symbol);
        StockAndOptionContracts stockAndOptionContracts = this.stockAndOptionContracts.get(symbol);
        if (stockAndOptionContracts == null) {
            stockAndOptionContracts = new StockAndOptionContracts();
        }
        stockAndOptionContracts.setContractDetails(contractDetails);

        this.stockAndOptionContracts.put(symbol, stockAndOptionContracts);
    }

    @Override
    public void securityDefinitionOptionalParameter(int reqId, String exchange,
                                                    int underlyingConId, String tradingClass, String multiplier,
                                                    Set<String> expirations, Set<Double> strikes) {
        System.out.println("Security Definition Optional Parameter. Request: "+reqId+", Trading Class: "+tradingClass+", Exchange: "+exchange+", Multiplier: "+multiplier+ ", Expirations: " + expirations + ", strikes: " + strikes + " \n");
        String symbol = stockContractIdToSymbol.get(underlyingConId);
        SecurityDefinitionOptionalParameter securityDefinitionOptionalParameter = new SecurityDefinitionOptionalParameter(reqId, symbol, exchange, multiplier, expirations, strikes);
        StockAndOptionContracts stockAndOptionContracts = this.stockAndOptionContracts.get(symbol);
        stockAndOptionContracts.setSecurityDefinitionOptionalParameter(securityDefinitionOptionalParameter);
    }

    @Override
    public void securityDefinitionOptionalParameterEnd(int reqId) {
        System.out.println("Security Definition Optional Parameter End. Request: " + reqId);
        String symbol = securityDefOptionalParameterReqIdToSymbol.get(reqId);
        StockAndOptionContracts stockAndOptionContracts = this.stockAndOptionContracts.get(symbol);
        if (stockAndOptionContracts.getSecurityDefinitionOptionalParameter() == null) {
            System.out.println("No options for: " + symbol);
            stockAndOptionContracts.setSecurityDefinitionOptionalParameter(SecurityDefinitionOptionalParameter.NO_OPTION);
        }
    }

    @Override
    public void tickPrice(int tickerId, int field, double price, TickAttrib attribs) {
        System.out.println("Tick Price. Ticker Id:"+tickerId+", Field: "+field+", Price: "+price+", CanAutoExecute: "+ attribs.canAutoExecute()
                + ", pastLimit: " + attribs.pastLimit() + ", pre-open: " + attribs.preOpen());
        String symbol = stockDataReqIdToSymbol.get(tickerId);
        if (symbol != null) {
            switch (field) {
                case 1: lastBidForStock.put(symbol, price);
                    break;
                case 2: lastAskForStock.put(symbol, price);
                    break;
                case 4: lastPriceForStock.put(symbol, price);
                    loadedPriceForAllStocks = lastPriceForStock.size() == symbols.size();
                    break;
                default:
            }
        }
    }

    @Override
    public void tickOptionComputation(int tickerId, int field, int tickAttrib,
                                      double impliedVol, double delta, double optPrice,
                                      double pvDividend, double gamma, double vega, double theta,
                                      double undPrice) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

        System.out.println(sdf.format(new Date()) + "TickOptionComputation. TickerId: "+tickerId+", field: "+field+", TickAttrib: "+tickAttrib+", ImpliedVolatility: "+impliedVol+", Delta: "+delta
                +", OptionPrice: "+optPrice+", pvDividend: "+pvDividend+", Gamma: "+gamma+", Vega: "+vega+", Theta: "+theta+", UnderlyingPrice: "+undPrice);
        String symbol = optionDataReqIdToSymbol.get(tickerId);
        if (symbol == null) {
            throw new RuntimeException("Unable to map reqID: " + tickerId + " to market data request!");
        }
        switch (field) {
            case 10: symbolToBidImpliedVol.put(symbol, impliedVol);
                break;
            case 11: symbolToAskImpliedVol.put(symbol, impliedVol);
                break;
            case 12: symbolToLastImpliedVol.put(symbol, impliedVol);
                break;
            case 13: symbolToModelImpliedVol.put(symbol, impliedVol);
                break;
            default:
        }

    }

    public Map<String, StockAndOptionContracts> getStockAndOptionContracts() {
        return stockAndOptionContracts;
    }

    public Map<Integer, String> getSecurityDefOptionalParameterReqIdToSymbol() {
        return securityDefOptionalParameterReqIdToSymbol;
    }

    public void setSecurityDefOptionalParameterReqIdToSymbol(Map<Integer, String> securityDefOptionalParameterReqIdToSymbol) {
        this.securityDefOptionalParameterReqIdToSymbol = securityDefOptionalParameterReqIdToSymbol;
    }

    public Map<Integer, String> getStockDataReqIdToSymbol() {
        return stockDataReqIdToSymbol;
    }

    public void setStockDataReqIdToSymbol(Map<Integer, String> stockDataReqIdToSymbol) {
        this.stockDataReqIdToSymbol = stockDataReqIdToSymbol;
    }

    public Set<String> getSymbols() {
        return symbols;
    }

    public Double getLastPriceForStock(String symbol) {
        return lastPriceForStock.get(symbol);
    }

    public boolean isLoadedPriceForAllStocks() {
        return loadedPriceForAllStocks;
    }

    public Map<Integer, String> getOptionDataReqIdToSymbol() {
        return optionDataReqIdToSymbol;
    }

    public Double getOptionStrikePrice(String symbol) {
        Double result = Double.MAX_VALUE;
        Double lastPrice = lastPriceForStock.get(symbol);
        StockAndOptionContracts stkOption = stockAndOptionContracts.get(symbol);
        Set<Double> strikes = stkOption.getSecurityDefinitionOptionalParameter().getStrikes();
        if (strikes == null) {
            System.out.println("How'd this happen!!!");
        }
        for (Double strike : strikes) {
            if (strike > lastPrice && strike < result) {
                result = strike;
            }
        }
        return  result;
    }

    public Map<String, Double> getSymbolToBidImpliedVol() {
        return symbolToBidImpliedVol;
    }

    public Map<String, Double> getSymbolToAskImpliedVol() {
        return symbolToAskImpliedVol;
    }

    public Map<String, Double> getSymbolToLastImpliedVol() {
        return symbolToLastImpliedVol;
    }

    public Map<String, Double> getSymbolToModelImpliedVol() {
        return symbolToModelImpliedVol;
    }
}
