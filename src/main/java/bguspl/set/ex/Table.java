package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Vector;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)
    
    /**
     * Mapping of all the tokens on specific indexes.
     */
    protected final Vector<Vector<Integer>> table; // slot per card (if any)
    
    /**
     * Signifies the player doesn't own the card. 
     */
    private final int cardNotFound = -1;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        table = new Vector<Vector<Integer>>();
        for (int i = 0; i < slotToCard.length; i++)
        	table.add(new Vector<Integer>());

    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        
        
        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        // TODO implement
        env.ui.placeCard(card, slot);
        
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        // TODO implement
        removeAllTokens(slot);
        slotToCard[slot] = null;
        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        // TODO implement
    	if (slotToCard[slot] == null)
    		return;
    	env.ui.placeToken(player, slot);
    	table.get(slot).add(player);
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        // TODO implement
    	if (slotToCard[slot] == null)
    		return false;
        
    	env.ui.removeToken(player, slot);
    	table.get(slot).remove((Integer)player);
    	return true;
    }
    
    // Removes all tokens of a specified card
    public boolean removeAllTokens(int slot) {
    	if (slotToCard[slot] == null)
    		return false;
    	table.get(slot).clear();
    	env.ui.removeTokens(slot);
    	return true;
    }
    
    // Returns all the cards that a specified player has a token on
    public Integer[] getPlayersCards(int id) {
    	
    	Integer[] slots = new Integer[Dealer.setSize];
    	for (int i = 0; i < slots.length; i++)
    		slots[i] = -1;
    	
    	int i = 0;
    	int j = 0;
    	for (Vector<Integer> slot : table) {
    		if (slot.contains((Integer)id)){
    				slots[j] = slotToCard[i];
    				j++;
    		}
    		i++;
    	}
    	return slots;
    }
    
    // Removes all the cards that are on the table
    public int[] removeAllCards() {
    	int[] cards = new int[countCards()];
    	int j = 0;
    	for (int i =0; i < slotToCard.length; i++)
    		if (slotToCard[i] != null) {
    			cards[j] = slotToCard[i];
    			removeCard(i);
    			j++;
    		}
    
    	return cards;
    }
    
    // Places the specified card on an empty spot
    public boolean placeCard(int card) {   
        int slot = -1;
        for (int i = 0 ; i < slotToCard.length ; i++)
        	if (slotToCard[i] == null) {
        		slot = i;
        		break;
        	}
        if (slot == -1)
        	return false;
        placeCard(card, slot);
        return true;
    }
    
    // Returns all the cards that a specified player has a token on
    public int getAmountOfPlayersCards(int id) {
    	Integer[] cards = getPlayersCards(id);
    	int size = 0;
    	for (int i = 0; i < cards.length; i++)
    		if (cards[i] != null && cards[i] != cardNotFound)
    			size++;

    	return size;
    }
    
    public boolean hasTokenOn(int id, int slot) {
    	if (table.get(slot).contains((Integer) id))
    			return true;	
    	return false;
    }
    
}
