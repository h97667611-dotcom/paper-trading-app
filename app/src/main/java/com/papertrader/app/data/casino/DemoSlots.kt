package com.papertrader.app.data.casino

/** One fictional demo slot. Everything about it is invented; no real money is involved. */
data class DemoSlot(
    val id: String,
    val name: String,
    val provider: String,
    val category: String,
    val theme: String,
    val description: String,
    val symbols: List<String>,
    val wild: String?,
    val scatter: String?,
    val reels: Int,
    val rows: Int,
    val mechanic: String,
    val minCluster: Int?
) {
    /** Highest-value symbol, used as the slot's icon. */
    val icon: String get() = symbols.last()
}

object DemoSlots {
    val all: List<DemoSlot> = listOf(
        DemoSlot("sweet-fruits", "Sweet Fruits", "Demo Studio Nova", "Fruits & Sweets", "Fruits", "Classic 3x3 fruit machine: match three on a line, wild stars substitute.", listOf("🍒", "🍋", "🍊", "🍇", "🔔", "7️⃣"), "⭐", null, 3, 3, "lines", null),
        DemoSlot("pharaohs-gold", "Pharaoh's Gold", "Demo Studio Atlas", "Adventure", "Egypt", "Explore a golden tomb with 5 reels and sun-god wilds. Scrolls trigger a bonus flag.", listOf("🐍", "🪲", "🏺", "👁️", "🔺", "👑"), "🌞", "📜", 5, 3, "lines", null),
        DemoSlot("galaxy-quest", "Galaxy Quest", "Demo Studio Orbit", "Sci-Fi", "Space", "Ways-to-win in deep space: matching symbols on neighbouring reels count in any row.", listOf("🪐", "☄️", "🛰️", "👽", "🚀", "🌟"), "🌌", "🛸", 5, 4, "ways", null),
        DemoSlot("pirate-treasure", "Pirate Treasure", "Demo Studio Atlas", "Adventure", "Pirates", "Sail the seas, collect treasure lines and find the island scatter.", listOf("⚓", "🧭", "🗺️", "🦜", "💰", "🏴‍☠️"), "🦑", "🏝️", 5, 3, "lines", null),
        DemoSlot("mystic-realm", "Mystic Realm", "Demo Studio Nova", "Fantasy", "Fantasy", "Elves, wizards and unicorns pay in ways across five reels.", listOf("🧝", "🏰", "📖", "🗡️", "🧙", "🦄"), "🔮", "✨", 5, 3, "ways", null),
        DemoSlot("deep-blue-reef", "Deep Blue Reef", "Demo Studio Pixel", "Nature", "Underwater", "6x5 scatter-pays grid: 8 or more matching sea creatures anywhere win.", listOf("🐚", "🦀", "🐠", "🐙", "🦈", "🐋"), "🧜", "🌊", 6, 5, "cluster", 8),
        DemoSlot("neon-cyber-grid", "Neon Cyber Grid", "Demo Studio Orbit", "Sci-Fi", "Cyberpunk", "Hack the grid: five reels of glitchy gear with DNA wilds.", listOf("💾", "🔋", "🤖", "👾", "🕶️", "⚡"), "🧬", "💽", 5, 3, "lines", null),
        DemoSlot("viking-saga", "Viking Saga", "Demo Studio Atlas", "History", "Vikings", "Raid with shields, mead and wolves. Mountains award a bonus flag.", listOf("🛡️", "🍺", "⚔️", "🪓", "🐺", "⚡"), "⛵", "🏔️", 5, 3, "lines", null),
        DemoSlot("jungle-run", "Jungle Run", "Demo Studio Pixel", "Nature", "Jungle", "Swing through the canopy: ways-to-win with palm wilds.", listOf("🍌", "🥥", "🦜", "🐒", "🐆", "🗿"), "🌴", "🌿", 5, 3, "ways", null),
        DemoSlot("robo-factory", "Robo Factory", "Demo Studio Nova", "Sci-Fi", "Robots", "Compact 3x3 robot assembly line with a light-bulb wild.", listOf("🔩", "⚙️", "🔧", "🔋", "🤖", "🦾"), "💡", null, 3, 3, "lines", null),
        DemoSlot("treasure-hunt", "Treasure Hunt", "Demo Studio Atlas", "Adventure", "Treasure", "Follow the map: keys, rings and crowns pay on five reels.", listOf("🪙", "🔑", "💍", "📜", "💎", "👑"), "🧭", "🗺️", 5, 3, "lines", null),
        DemoSlot("dragon-fire", "Dragon Fire", "Demo Studio Orbit", "Fantasy", "Dragons", "Tall 5x4 reels where fire and dragons pay in ways.", listOf("🥚", "🔥", "🛡️", "🗡️", "🐉", "💎"), "🐲", "🌋", 5, 4, "ways", null),
        DemoSlot("wild-west-rush", "Wild West Rush", "Demo Studio Pixel", "History", "Western", "Sheriffs, horses and gold nuggets across a dusty desert.", listOf("🌵", "🐴", "🔫", "⭐", "🤠", "💰"), "🐂", "🏜️", 5, 3, "lines", null),
        DemoSlot("candy-pop", "Candy Pop", "Demo Studio Nova", "Fruits & Sweets", "Sweets", "6x5 candy cluster: 8 or more matching sweets anywhere win.", listOf("🍬", "🍭", "🍩", "🍪", "🧁", "🍫"), "🍰", "🎂", 6, 5, "cluster", 8),
        DemoSlot("neon-nights", "Neon Nights", "Demo Studio Orbit", "Retro", "Neon", "Retro 4x3 neon lights with glowing orbs and lightning wilds.", listOf("🔴", "🟠", "🟡", "🟢", "🔵", "🟣"), "⚡", "💫", 4, 3, "lines", null),
        DemoSlot("samurai-blade", "Samurai Blade", "Demo Studio Atlas", "History", "Samurai", "Blades, masks and cherry-blossom wilds under the torii gate.", listOf("🎋", "🍙", "🏯", "🗡️", "👺", "🥷"), "🌸", "⛩️", 5, 3, "lines", null),
        DemoSlot("lost-horizon", "Lost Horizon", "Demo Studio Pixel", "Adventure", "Adventure", "Gear up for the expedition: ways-to-win with sunrise wilds.", listOf("🧭", "🔦", "⛺", "🗻", "🪂", "🎒"), "🌅", "🧗", 5, 3, "ways", null),
        DemoSlot("dino-island", "Dino Island", "Demo Studio Pixel", "Nature", "Dinosaurs", "Prehistoric 5x4 lines with fossils, volcanoes and big predators.", listOf("🥚", "🌿", "🦴", "🦕", "🦖", "🌋"), "🦎", "☄️", 5, 4, "lines", null),
        DemoSlot("arcane-library", "Arcane Library", "Demo Studio Nova", "Fantasy", "Magic", "Compact 4x4 library of spells, owls and wands.", listOf("📜", "🕯️", "🧪", "🔮", "🪄", "🦉"), "🌙", "📖", 4, 4, "lines", null),
        DemoSlot("future-city-2099", "Future City 2099", "Demo Studio Orbit", "Sci-Fi", "Future", "Skyline 6x4 ways-to-win with hover cars and gene wilds.", listOf("🚗", "🛰️", "🏙️", "🤖", "🧬", "🌐"), "⚛️", "🛸", 6, 4, "ways", null)
    )

    fun byId(id: String): DemoSlot? = all.firstOrNull { it.id == id }
}
