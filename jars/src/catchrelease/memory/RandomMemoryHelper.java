package catchrelease.memory;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import java.util.Random;

public class RandomMemoryHelper {
    public static final String MEM_KEY_RANDOM = "$catchrelease_random";

    public static Random getRandom(StarSystemAPI system){
        return getRandom(system.getMemoryWithoutUpdate());
    }

    private static Random getRandom(MemoryAPI memory){
        Random random;

        if (memory.contains(MEM_KEY_RANDOM)) random = (Random) memory.get(MEM_KEY_RANDOM);
        else {
            random = new Random();
            memory.set(MEM_KEY_RANDOM, random);
        }

        return random;
    }
}
