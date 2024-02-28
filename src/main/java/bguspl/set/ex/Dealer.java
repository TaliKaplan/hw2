package bguspl.set.ex;

import bguspl.set.Env;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.plaf.synth.SynthOptionPaneUI;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;


/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;
    
    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    
    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private volatile boolean shuffle;
    
    /**
     * The time for the dealer to wake up to update the timer if nobody woke him up
     */
    private final int wakeUpTime = 500;
    
    /**
     * The size of each set in the game.
     */
    public static int setSize = 3;
    
    /**
     * Used to find out if a single set is still in the deck.
     */
    public static final int findASet = 1;
    
    /**
     * Queue of who made a set first.
     */
    private Vector<Integer> playerCheckQueue;
    
    /**
     * Signifies the first element 
     */
    private final int first = 0;
    
    /**
     * Keeps the players threads 
     */
    private LinkedList<Thread> playersThreads;
    
    /**
     * Signifies if the alarm timer needs to be turned on
     */
    private boolean warn;
    
    /**
     * Waking up the dealer to update the timer faster when the warning flag is on 
     */
    private final int fastWakeUp = 80;

    /**
     * Signified that a card was not founds
     */
    private final int notFound = -1;
    
    /**
     * Signified a not negative number
     */
    private final int nonNegative = 0;

    private final List<Integer> cardsToRemove;
    public boolean terminated = false; 
    
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        shuffle = true;
        playerCheckQueue = new Vector<Integer>();
        playersThreads = new LinkedList<Thread>();
        setSize = env.config.featureSize;
        warn = false;
        cardsToRemove = new LinkedList<>();

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for(Player play : players) {
        	Thread player = new Thread(play);
        	playersThreads.addLast(player);
            player.start();
        }
        
        shuffle = true;
        // first placing of the cards
        placeCardsOnTable();
        ensureSetOnTable();
        updateTimerDisplay(true);
        
        while (!terminate) {
        	displayHints();
            shuffle = false;
        	notifyPlayers();
            timerLoop();
            shuffle = true;
            removeAllCardsFromTable();
            placeCardsOnTable();
            ensureSetOnTable();
            updateTimerDisplay(true);
        }
        
        if(!terminated)
            terminate();
        announceWinners();
        try {
        	Thread.currentThread().sleep(env.config.endGamePauseMillies);
        }
        catch(InterruptedException eror) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) { 
        	sleepUntilWokenOrTimeout();
            checkForSet();
            updateTimerDisplay(false);
            //removeCardsFromTable();
            //placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    	terminate = true;
        terminated = true;
        shuffle = false;
        
        for (int i = 0; i < playersThreads.size(); i++) {
    		players[i].terminate();
            players[i].notifyAi();
            synchronized(players[i]) {
                players[i].notify();
    		}
    		try {
    			playersThreads.get(i).join();
    		}
    		catch(InterruptedException error) {}
    	}
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
    	for (Integer slot : cardsToRemove) {
            table.removeCard(slot);
        }

        cardsToRemove.clear();
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
    	int numToPlace = env.config.tableSize - table.countCards();
    	// we are reshuffling and no more sets are available in the deck
    	if (numToPlace == env.config.tableSize && env.util.findSets(deck, findASet).size() == 0) {
    		terminate = true;
    		return;
    	}
    	//only shuffle if all the cards need to be replaced
    	if (numToPlace == env.config.tableSize)
    		Collections.shuffle(deck);
    	// places a randomly chosen card from the deck on the table
    	for (int i = 0; i < numToPlace && !deck.isEmpty(); i++)
    		table.placeCard(deck.remove(first));
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
    	synchronized(this) {
    		if (playerCheckQueue.isEmpty()) {
	    		try {
	    			if (warn)
	    				wait(fastWakeUp);
	    			else
	    				wait(wakeUpTime);
	    		}
	    		// a player woke him up
	    		catch(InterruptedException error){
	    		}
    		}
    	}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
    	long timeLeft = reshuffleTime - System.currentTimeMillis();
    	if (timeLeft == 0 || reset) {
    		reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
    	}
    	
		timeLeft = reshuffleTime - System.currentTimeMillis();
		if (timeLeft < nonNegative)
			timeLeft = nonNegative;
		// idk if this needs to be displaced once or when lower
		if (timeLeft <= env.config.turnTimeoutWarningMillis) {
			warn = true;
			env.ui.setCountdown(timeLeft, true);}
		else {
			warn = false;
			env.ui.setCountdown(timeLeft, false);}
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
    	for (int card :table.removeAllCards())
    		deck.add(card);
    	
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int max = 0;
    	LinkedList<Integer> winners = new LinkedList<Integer>();
    	// Find the max score and adds the winners
        for (Player player : players) {
        	if (player.score() >= max) {
        		if (player.score() > max){
        			winners.clear();
        		    max = player.score();
                }
        		winners.add(player.id);
        	}
        }
        
        int[] ids = new int[winners.size()];
        for(int i = 0; i < winners.size(); i++) 
        	ids[i] = winners.get(i);
        
        env.ui.announceWinner(ids);
    }
    
    // Returns if the deck is being reshuffled
    public boolean shuffleStatus() {
    	return shuffle;
    }
    
    // Checks if a player completed a set
    private void checkForSet() {
    	if (playerCheckQueue.isEmpty())
    		return;
    	
    	int playerId = playerCheckQueue.remove(first);
    	Player player = players[playerId];
    	Integer[] cards = table.getPlayersCards(playerId);
    	// checks there are indeed 3 cards
    	for (int i = 0; i < cards.length; i++)
    		if (cards[i] == null || cards[i] == notFound)
    			return;
    	
        int[] intCards = new int[cards.length];
        for(int i=0; i<cards.length; i++){
            intCards[i] = cards[i];
        }

    	boolean result = env.util.testSet(intCards);
    	// Legal set
    	if (result) {
        	synchronized(player) {
        		player.setWon(true);
        		player.notify();
        	}
    		
    		for (int card : cards) 
    			cardsToRemove.add(table.cardToSlot[card]);
            
            removeCardsFromTable();
    		
            placeCardsOnTable();
    		updateTimerDisplay(true);
            displayHints();
    		return;
    	}
    	
    	// illegal set
    	synchronized(player) {
    		player.setWon(false);
    		player.notify();
    	}
    }

    // Notifys all the players
    private void notifyPlayers() {
    	for (Player player : players) {
    		synchronized(player) {
    			player.clearActions();
    			player.notify();
    			player.notifyAi();
    		}
    	}
    		
    }
    
    // adds a player to the queue of which player to check first
    public void addPlayerToCheck(int id) {
    	playerCheckQueue.add(id);
    }
    
    private void ensureSetOnTable(){
        List<Integer> cards = new LinkedList<>();
        for(Integer card: table.slotToCard){
            if(card != null)
                cards.add(card);
        }

        while (!terminate && env.util.findSets(cards, findASet).isEmpty()) {
            removeAllCardsFromTable();
            Collections.shuffle(deck);
            placeCardsOnTable();

            cards.clear();
            for(Integer card: table.slotToCard){
                if(card != null)
                    cards.add(card);
            }
        }
    }


    private void displayHints(){
        if(env.config.hints)
            table.hints();
    }
}
