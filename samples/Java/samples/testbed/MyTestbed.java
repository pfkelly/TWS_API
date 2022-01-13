/* Copyright (C) 2019 Interactive Brokers LLC. All rights reserved. This code is subject to the terms
 * and conditions of the IB API Non-Commercial License or the IB API Commercial License, as applicable. */

package samples.testbed;

import com.ib.client.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class MyTestbed {

	private static int requestId = 1;
	private static DateTimeFormatter YearMonthDay = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static long minDaysToExpiration = 49;
	private static long maxDaysToExpiration = 71;

	public static void main(String[] args) throws InterruptedException {
		String[] javaClassPaths = System.getProperty("java.class.path").split(";");
		for (String javaClassPath : javaClassPaths) {
			System.out.println(javaClassPath);
		}
		MyEWrapper wrapper = new MyEWrapper(args[0]);

		final EClientSocket m_client = wrapper.getClient();
		final EReaderSignal m_signal = wrapper.getSignal();
		//! [connect]
		m_client.eConnect("127.0.0.1", 7497, 2);
		//! [connect]
		//! [ereader]
		final EReader reader = new EReader(m_client, m_signal);   
		
		reader.start();
		//An additional thread is created in this program design to empty the messaging queue
		new Thread(() -> {
		    while (m_client.isConnected()) {
		        m_signal.waitForSignal();
		        try {
		            reader.processMsgs();
		        } catch (Exception e) {
		            System.out.println("Exception: "+e.getMessage());
		        }
		    }
		}).start();
		//! [ereader]
		// A pause to give the application time to establish the connection
		// In a production application, it would be best to wait for callbacks to confirm the connection is complete
		Thread.sleep(1000);

//		getStaticOptionChains(wrapper);
		getStockContracts(wrapper);
		getMarketDataForStocks(wrapper);
		getOptionChains(wrapper);
		requestMarketDataForOptions(wrapper);
		waitForOptionMarketData(wrapper);

//		optionsOperations(wrapper);

		Thread.sleep(100000);

		m_client.eDisconnect();
		System.out.println("Exiting...");
	}

	private static void waitForOptionMarketData(MyEWrapper wrapper) throws InterruptedException {
		int optionsToCheck = wrapper.getOptionDataReqIdToSymbol().size();
		int optionModelVolLoaded = wrapper.getSymbolToModelImpliedVol().size();
		while (optionsToCheck >optionModelVolLoaded) {
			Thread.sleep(100);
			System.out.println("optionsToCheck: " + optionsToCheck + "optionModelVolLoaded: " + optionModelVolLoaded);
			optionModelVolLoaded = wrapper.getSymbolToModelImpliedVol().size();
		}
		Thread.sleep(1000);
	}

	private static void requestMarketDataForOptions(MyEWrapper wrapper) {
		EClientSocket client = wrapper.getClient();
		Collection<StockAndOptionContracts> stockAndOptionContracts = wrapper.getStockAndOptionContracts().values();
		LocalDate today = LocalDate.now();
		for (StockAndOptionContracts stockAndOptionContract : stockAndOptionContracts) {
			String symbol = stockAndOptionContract.getContractDetails().contract().symbol();
			Set<String> expirations = stockAndOptionContract.getSecurityDefinitionOptionalParameter().getExpirations();
			if (expirations == null) {
				System.out.println(symbol + " does not have any options");
			} else {
				Double strike = wrapper.getOptionStrikePrice(symbol);
				boolean requestedMktDataForThisSymbol = false;
				for (String expirationDateStr : expirations) {
					LocalDate expirationDate = LocalDate.parse(expirationDateStr, YearMonthDay);
					long daysTilExpiration = ChronoUnit.DAYS.between(today, expirationDate);
					if (daysTilExpiration > minDaysToExpiration && daysTilExpiration < maxDaysToExpiration) {
						Contract optionContract = new Contract();
						optionContract.symbol(symbol);
						optionContract.currency("USD");
						optionContract.secType(Types.SecType.OPT);
						optionContract.exchange("SMART");
						optionContract.right(Types.Right.Call);
						optionContract.lastTradeDateOrContractMonth(expirationDateStr);
						optionContract.strike(strike);
						wrapper.getOptionDataReqIdToSymbol().put(requestId, symbol);
						System.out.println("Option MKT DATA REQ: " +requestId + ", " + symbol + ", " + expirationDateStr + ", strike: " + strike);
						client.reqMktData(requestId++, optionContract, "", false, false, null);
						requestedMktDataForThisSymbol = true;
					}
				}
				if (!requestedMktDataForThisSymbol) {
					System.out.println(symbol + " does not have any options within date range");
				}
			}
		}
		System.out.println("Done requesting option market data reqID: " + (requestId - 1));
	}

	private static void getMarketDataForStocks(MyEWrapper wrapper) throws InterruptedException {
		EClientSocket client = wrapper.getClient();
		Map<Integer, String> mktDataReqIdStockToSymbol = wrapper.getStockDataReqIdToSymbol();
		Collection<StockAndOptionContracts> stockAndOptionContracts = wrapper.getStockAndOptionContracts().values();
		for (StockAndOptionContracts stockAndOptionContract : stockAndOptionContracts) {
			ContractDetails contractDetails = stockAndOptionContract.getContractDetails();
			Contract contract = contractDetails.contract();
			mktDataReqIdStockToSymbol.put(requestId, contract.symbol());
			client.reqMktData(requestId++, contract, "", false, false, null);
		}
		// TODO: Add timeout logic

		while(!wrapper.isLoadedPriceForAllStocks()) {
			Thread.sleep(100);
		}
		// Cancel market data
		for (Integer reqId : mktDataReqIdStockToSymbol.keySet()) {
			client.cancelMktData(reqId);
		}
		System.out.println("loaded all the Prices");
	}

	private static void getStockContracts(MyEWrapper wrapper) {
		Set<String> symbols = wrapper.getSymbols();
		for (String symbol: symbols) {
			Contract contract = new Contract();
			contract.secType(Types.SecType.STK);
			contract.symbol(symbol);
			contract.exchange("SMART");
			contract.currency("USD");
			wrapper.getClient().reqContractDetails(requestId++, contract);
		}
		boolean loadingStockMarketData = true;
		Map<String, StockAndOptionContracts> stockAndOptionContracts = wrapper.getStockAndOptionContracts();
		while (loadingStockMarketData) {
			loadingStockMarketData = false;
			for(Map.Entry<String, StockAndOptionContracts> entry : stockAndOptionContracts.entrySet()) {
				if (entry.getValue() == null || entry.getValue().getContractDetails() == null) {
					loadingStockMarketData = true;
					break;
				}
			}
		}
	}

private static void getOptionChains(MyEWrapper wrapper) throws InterruptedException {
		EClientSocket client = wrapper.getClient();
		Map<String, StockAndOptionContracts> stockAndOptionContracts = wrapper.getStockAndOptionContracts();
		Collection<StockAndOptionContracts> stockContracts = stockAndOptionContracts.values();
		Map<Integer, String> securityDefOptionalParameterReqIdToSymbol = wrapper.getSecurityDefOptionalParameterReqIdToSymbol();
		for (StockAndOptionContracts stockContract : stockContracts) {
			Contract contract = stockContract.getContractDetails().contract();
			securityDefOptionalParameterReqIdToSymbol.put(requestId, contract.symbol());
			client.reqSecDefOptParams(requestId++, contract.symbol(), "", Types.SecType.STK.getApiString(), contract.conid());
		}
		boolean optionChainsLoading = true;
		while (optionChainsLoading) {
			optionChainsLoading = false;
			Thread.sleep(100);
			for (Map.Entry<String, StockAndOptionContracts> entry : stockAndOptionContracts.entrySet()) {
				if (entry.getValue() == null || entry.getValue().getSecurityDefinitionOptionalParameter() == null) {
					optionChainsLoading = true;
					break;
				}
			}
		}
	}

	private static void optionsOperations(MyEWrapper wrapper) {

		EClientSocket client =  wrapper.getClient();
		String symbol = "AHT";

		//! [reqsecdefoptparams]
		Contract zillow = new Contract();
		zillow.secType(Types.SecType.STK);
		zillow.symbol(symbol);
		zillow.exchange("SMART");

		client.reqContractDetails(1, zillow);

		Contract contract = new Contract();
		contract.symbol(symbol);
		contract.secType("OPT");
		contract.currency("USD");
		contract.exchange("SMART");
		contract.lastTradeDateOrContractMonth("202203");
		contract.right(Types.Right.Call);
		contract.strike(10.50);

		client.reqSecDefOptParams(2, "ZG", "", "STK", 184072087);
//		client.reqSecDefOptParams(0, "IBM", "", "STK", 8314);
		//! [reqsecdefoptparams]

//		Contract zillowOption = new Contract();
//		zillowOption.secType(Types.SecType.STK);
//		zillowOption.symbol("ZG");
//		zillowOption.exchange("SMART");
//		zillowOption.currency("USD");
//		zillowOption.lastTradeDateOrContractMonth("202203");
//		client.reqContractDetails(2, zillowOption);

		//! [calculateimpliedvolatility]
//		client.calculateImpliedVolatility(5001, ContractSamples.OptionWithLocalSymbol(), 0.6, 55, null);
		//! [calculateimpliedvolatility]
		
		//** Canceling implied volatility ***
//		client.cancelCalculateImpliedVolatility(5001);
		
		//! [calculateoptionprice]
//		client.calculateOptionPrice(5002, ContractSamples.OptionWithLocalSymbol(), 0.5, 55, null);
		//! [calculateoptionprice]
		
		//** Canceling option's price calculation ***
//		client.cancelCalculateOptionPrice(5002);
		
		//! [exercise_options]
		//** Exercising options ***
//		client.exerciseOptions(5003, ContractSamples.OptionWithTradingClass(), 1, 1, "", 1);
		//! [exercise_options]
	}

}
