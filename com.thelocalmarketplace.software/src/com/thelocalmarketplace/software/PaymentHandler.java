package com.thelocalmarketplace.software;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.tdc.CashOverloadException;
import com.tdc.ComponentFailure;
import com.tdc.DisabledException;
import com.tdc.NoCashAvailableException;
import com.tdc.coin.Coin;
import com.thelocalmarketplace.hardware.SelfCheckoutStation;

import ca.ucalgary.seng300.simulation.NullPointerSimulationException;
import ca.ucalgary.seng300.simulation.SimulationException;

/**
 * Manages the payment process with coins for a self-checkout system.
 * Handles coin insertion, validation, and change dispensing.
 */
public class PaymentHandler extends SelfCheckoutStation {
	
	public BigDecimal amountSpent;
	private BigDecimal changeRemaining = BigDecimal.ZERO;
	private BigDecimal totalCost = BigDecimal.ZERO;
	private SelfCheckoutStation checkoutSystem = null;
	
	public PaymentHandler(SelfCheckoutStation station) {
		if(station == null) throw new NullPointerException("No argument may be null.");
		this.checkoutSystem = station;
	}
	
	/**
	 * will be used to help with Signaling to the Customer the updated amount 
	 * due after the insertion of each coin.
	 * @return money left to pay
	*/
	public BigDecimal getChangeRemaining() {
		return this.changeRemaining;
	}

	/**
	 * Processes payment using coins inserted by the customer.
	 * 
	 * @param coinsList List of coins inserted by the customer.
	 * @return true if payment is successful, false otherwise.
	 * @throws DisabledException If the coin slot is disabled.
	 * @throws CashOverloadException If the cash storage is overloaded.
	 * @throws NoCashAvailableException If no cash is available for dispensing change.
	 */
	public boolean processPaymentWithCoins(ArrayList<Coin> coinsList) throws DisabledException, CashOverloadException, NoCashAvailableException {
		if(coinsList == null) throw new NullPointerException("coinsList cannot be null."); // Check for null parameters.
		BigDecimal value = new BigDecimal("0");
		for(Coin coin : coinsList) { // Calculate the total value of coins inserted.
			value = value.add(coin.getValue());
		}
		
		this.amountSpent = value;
		this.changeRemaining = value.subtract(this.totalCost);

		
		boolean isSuccess = false;
		for(Coin coin : coinsList) { // Accept each coin inserted by the customer.
			isSuccess = acceptInsertedCoin(coin);
			if(!isSuccess) value = value.subtract(coin.getValue());
		}
		
		if(value.compareTo(this.totalCost) < 0) return false; // Return false if the total value of valid coins is less than the total cost.
		
		this.amountSpent = this.totalCost;

		// Return true if accurate change is dispensed.
		if(value.compareTo(this.totalCost) > 0) {
			BigDecimal changeValue = value.subtract(this.totalCost);
			return dispenseAccurateChange(changeValue);
		}
		return true;
	}
	
	/**
	 * Accepts a coin inserted by the customer into the coin slot.
	 * 
	 * @param coin The coin to be validated and accepted.
	 * @return true if the coin is successfully accepted, false otherwise.
	 * @throws DisabledException If the coin slot is disabled.
	 * @throws CashOverloadException If the cash storage is overloaded.
	 */
	private boolean acceptInsertedCoin(Coin coin) throws DisabledException, CashOverloadException { 
		if(this.checkoutSystem.coinStorage.hasSpace()) {
			this.checkoutSystem.coinSlot.receive(coin);
		} else {
			this.checkoutSystem.coinSlot.disable();
		}
		return false;
	}

	/**
	 * Dispenses the correct amount of change to the customer.
	 * 
	 * Implements change dispensing logic using available coin denominations.
	 * 
	 * @param changeValue The amount of change to be dispensed.
	 * @return true if correct change is dispensed, false otherwise.
	 * @throws DisabledException If the coin slot is disabled.
	 * @throws CashOverloadException If the cash storage is overloaded.
	 * @throws NoCashAvailableException If no cash is available for dispensing change.
	 */
	public boolean dispenseAccurateChange(BigDecimal changeValue) throws DisabledException, CashOverloadException, NoCashAvailableException {
		BigDecimal amountDispensed = new BigDecimal("0.0");
		BigDecimal remainingAmount = changeValue;
		List<BigDecimal> coinDenominations = this.checkoutSystem.coinDenominations;
		Collections.sort(coinDenominations);
		Collections.reverse(coinDenominations);

		if(remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal lowestCoin = coinDenominations.get(coinDenominations.size() - 1);
			if(remainingAmount.compareTo(lowestCoin) < 0) {
				this.checkoutSystem.coinDispensers.get(lowestCoin).emit();
				amountDispensed = changeValue;
				remainingAmount = BigDecimal.ZERO;
			}
			for(int i = 0; i < coinDenominations.size(); i++) {
				BigDecimal val = coinDenominations.get(i);
				while(remainingAmount.compareTo(val) >= 0 && this.checkoutSystem.coinDispensers.get(val).size() > 0) {
					this.checkoutSystem.coinDispensers.get(val).emit();
					amountDispensed = amountDispensed.add(val);
					remainingAmount = remainingAmount.subtract(val);
					i = coinDenominations.size();
				}
			}
		}
		return (remainingAmount.compareTo(BigDecimal.ZERO) == 0);
	}

	
	/**
	 * Loads coins into the coin dispensers for change.
	 * 
	 * @param coins Coins to be loaded into the dispensers.
	 * @throws CashOverloadException If the coin dispensers are overloaded with coins.
	 */
	public void loadCoinDispenser(Coin... coins) throws CashOverloadException {
		if (coins == null) {
			throw new NullPointerSimulationException("coins instance cannot be null.");
		}
		for (Coin c: coins) {
			if (c == null) {
				throw new NullPointerSimulationException("coin instance cannot be null.");
			}
			BigDecimal v = c.getValue();
			try {
				this.checkoutSystem.coinDispensers.get(v).load(c);
			} catch (CashOverloadException e) {
				throw new CashOverloadException("Coin Dispenser for coins of value " + v.doubleValue() + " is full.");
			} catch (SimulationException e) {
				throw new ComponentFailure("This coin type does not exist.");
			}
		}
	}
	
	/**
	 * Empties the coin storage unit.
	 */
	public void emptyCoinStorage() {
		this.checkoutSystem.coinStorage.unload();

	}
	
}
