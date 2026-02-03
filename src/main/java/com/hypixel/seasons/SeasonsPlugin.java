package com.hypixel.seasons;

import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.util.logging.Logger;

// Entry point for seasons mod
// extends SeasonsModule and that's pretty much it
public class SeasonsPlugin extends SeasonsModule {

  private static final Logger logger = Logger.getLogger("SeasonsPlugin");

  public SeasonsPlugin(JavaPluginInit init) {
    super(init);
    // System.out.println("SeasonsPlugin constructor called");
  }

  // TODO: maybe add config loading here later idk
  // or just leave it in SeasonsModule who knows
}
