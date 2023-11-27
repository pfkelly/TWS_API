package samples.testbed;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EWrapperMsgGenerator;
import com.ib.client.TickAttrib;

import java.text.SimpleDateFormat;
import java.util.*;

import static java.lang.Thread.sleep;

public class MyEWrapper extends EWrapperImpl{

    private SortedMap<Integer, String> sortedSymbols = Collections.synchronizedSortedMap(new TreeMap<>());
    private Map<Integer, String> contractRequestIdToSymbol = new Hashtable<>();
    private Map<String, Contract> symbolToContract = new Hashtable<>();

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

    public MyEWrapper(String symbolArg) {
        super();
        String[] splitSymbols = symbolArg.split(",");
        for (int i = 0; i < splitSymbols.length; i++) {
            String symbol = splitSymbols[i];
            stockAndOptionContracts.put(symbol, null);
            sortedSymbols.put(i, symbol);
        }
    }

    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        System.out.println(EWrapperMsgGenerator.contractDetails(reqId, contractDetails));
        String symbol = contractDetails.contract().symbol();
        symbolToContract.put(symbol, contractDetails.contract());
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

    @Override
    public void error(int id, int errorCode, String errorMsg) {
        System.out.println("Error. Id: " + id + ", Code: " + errorCode + ", Msg: " + errorMsg + "\n");
        String symbol = contractRequestIdToSymbol.get(id);
        if (symbol != null) {
            Contract emptyContract = new Contract();
            emptyContract.symbol(symbol);
            symbolToContract.put(symbol, emptyContract);
            System.out.println("No contract found for symbol: " + symbol);
        }
    }

    Set<Contract> getContractsWithPrice() {
       Set<Contract> contracts = Collections.synchronizedSet(new TreeSet<>());
        for (Contract contract : symbolToContract.values()) {
            if (calculateLastPriceForStock(contract.symbol()) != null) {
                contracts.add(contract);
            }
        }
        return contracts;
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

    public SortedMap<Integer, String> getSortedSymbols() {
        return sortedSymbols;
    }

    public boolean isLoadedPriceForAllStocks() {
        return lastPriceForStock.size() == numberOfValidatedSymbols();
    }

    public boolean isLoadedBidAndAskForAllStocks() {
        long numberOfValidatedSymbols = numberOfValidatedSymbols();
        return numberOfValidatedSymbols == lastBidForStock.size() && numberOfValidatedSymbols == lastAskForStock.size();
    }

    public Map<Integer, String> getOptionDataReqIdToSymbol() {
        return optionDataReqIdToSymbol;
    }

    public Double getOptionStrikePrice(String symbol) {
        Double result = Double.MAX_VALUE;
        Double lastPrice = calculateLastPriceForStock(symbol);
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

    public void removeContractsWithoutCalculatedLastPrice() {
        boolean loadedLastPriceForAllStocks = true;
        for (String symbol : sortedSymbols.values()) {
            if (calculateLastPriceForStock(symbol) == null) {
                System.out.println("Removing Symbol - unable to retrieve stock price: " + symbol);
                stockAndOptionContracts.remove(symbol);
                loadedLastPriceForAllStocks = false;
            }
        }
        if(loadedLastPriceForAllStocks) {
            System.out.println("Was able to load lastPrice for all Stocks");
        }
    }

    private Double calculateLastPriceForStock(String symbol) {
        Double result = lastPriceForStock.get(symbol);
        if (result == null){
            Double lastBid = lastBidForStock.get(symbol);
            Double lastAsk = lastAskForStock.get(symbol);
            if (lastAsk == null || lastBid == null) {
                return result;
            }
            result = lastAsk / 2 + lastBid / 2;
        }
        return result;
    }

    private long numberOfValidatedSymbols() {
        return symbolToContract.values()
                .stream()
                .filter(c -> c.conid() != 0)
                .count();
    }

    public boolean haveDataToCreateAllOptionOrders() {
        int numberOfOptions = getOptionDataReqIdToSymbol().size();
        return numberOfOptions == getSymbolToModelImpliedVol().size() &&
                numberOfOptions == getSymbolToAskImpliedVol().size() &&
                numberOfOptions == getSymbolToBidImpliedVol().size();
    }

    public Collection<String> getUnloadedVolSymbols() {
        Collection<String> optionDataRequestSymbols = new HashSet<>(optionDataReqIdToSymbol.values());
        optionDataRequestSymbols.removeAll(symbolToModelImpliedVol.keySet());
        optionDataRequestSymbols.removeAll(symbolToAskImpliedVol.keySet());
        optionDataRequestSymbols.removeAll(symbolToBidImpliedVol.keySet());
        return optionDataRequestSymbols;
    }

    public Map<Integer, String> getContractRequestIdToSymbol() {
        return contractRequestIdToSymbol;
    }

    public Map<String, Contract> getSymbolToContract() {
        return symbolToContract;
    }
}
