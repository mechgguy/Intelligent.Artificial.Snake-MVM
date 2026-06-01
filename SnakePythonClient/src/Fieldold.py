# from dataclasses import dataclass
# from typing import Dict, List, Tuple

# from data_structures import Coord, TeamName


# @dataclass
# class ActiveEffectInfo:
#     effect: str
#     remaining_ticks: int


# @dataclass
# class SnakeInfo:
#     body: List[Coord]
#     alive: bool
#     inventory: List[str]
#     active_effects: List[ActiveEffectInfo]


# def fill_info_from_source(source) -> [SnakeInfo]:
#     snakes = {
#         team: SnakeInfo(
#             body=[tuple(coord) for coord in info["body"]],
#             alive=info["alive"],
#             inventory=list(info["inventory"]),
#             active_effects=[
#                 ActiveEffectInfo(
#                     effect=effect["effect"],
#                     remaining_ticks=effect["remaining_ticks"],
#                 )
#                 for effect in info["active_effects"]
#             ],
#         )
#         for team, info in source.items()
#     }
#     return snakes


# @dataclass
# class Field:
#     size: Tuple[int, int]
#     snakes: Dict[TeamName, SnakeInfo]

#     @staticmethod
#     def from_dict(raw: dict) -> "Field":
#         size = tuple(raw["size"])
#         snakes = ""
#         if "snake" in raw:
#             source = raw["snake"]
#             snakes = fill_info_from_source(source)
#         elif "snakes" in raw:
#             source = raw["snakes"]
#             snakes = fill_info_from_source(source)

#         return Field(size=size, snakes=snakes)


# from dataclasses import dataclass, field
# from typing import Dict, List, Tuple

# from data_structures import Coord, TeamName

# @dataclass
# class ActiveEffectInfo:
#     effect: str
#     remaining_ticks: int

# @dataclass
# class SnakeInfo:
#     body: List[Coord]
#     alive: bool
#     inventory: List[str]
#     active_effects: List[ActiveEffectInfo]

# def fill_info_from_source(source) -> Dict[TeamName, SnakeInfo]:
#     snakes = {
#         team: SnakeInfo(
#             body=[tuple(coord) for coord in info["body"]],
#             alive=info["alive"],
#             inventory=list(info["inventory"]),
#             active_effects=[
#                 ActiveEffectInfo(
#                     effect=effect["effect"],
#                     remaining_ticks=effect["remaining_ticks"],
#                 )
#                 for effect in info["active_effects"]
#             ],
#         )
#         for team, info in source.items()
#     }
#     return snakes

# @dataclass
# class Field:
#     size: Tuple[int, int]
#     snakes: Dict[TeamName, SnakeInfo]
#     apples: List[Coord] = field(default_factory=list)  # <-- Added this line

#     @staticmethod
#     def from_dict(raw: dict) -> "Field":
#         size = tuple(raw["size"])
        
#         snakes = {}
#         if "snake" in raw:
#             snakes = fill_info_from_source(raw["snake"])
#         elif "snakes" in raw:
#             snakes = fill_info_from_source(raw["snakes"])

#         # Capture the genuine apple array from the server payload
#         raw_apples = raw.get("apple", raw.get("apples", []))
#         apples = [tuple(coord) for coord in raw_apples]

#         return Field(size=size, snakes=snakes, apples=apples)


# BAD APPLES FIELD
# from dataclasses import dataclass, field
# from typing import Dict, List, Tuple

# from data_structures import Coord, TeamName

# @dataclass
# class ActiveEffectInfo:
#     effect: str
#     remaining_ticks: int

# @dataclass
# class SnakeInfo:
#     body: List[Coord]
#     alive: bool
#     inventory: List[str]
#     active_effects: List[ActiveEffectInfo]

# def fill_info_from_source(source) -> Dict[TeamName, SnakeInfo]:
#     snakes = {}
#     # Handle list format from server gracefully
#     if isinstance(source, list):
#         for idx, info in enumerate(source):
#             team = info.get("name", f"Bot_{idx}")
#             snakes[team] = SnakeInfo(
#                 body=[tuple(coord) for coord in info["body"]],
#                 alive=info.get("alive", True),
#                 inventory=list(info.get("inventory", [])),
#                 active_effects=[
#                     ActiveEffectInfo(
#                         effect=effect["effect"],
#                         remaining_ticks=effect["remaining_ticks"],
#                     )
#                     for effect in info.get("active_effects", [])
#                 ],
#             )
#     # Handle standard dictionary format
#     elif isinstance(source, dict):
#         for team, info in source.items():
#             snakes[team] = SnakeInfo(
#                 body=[tuple(coord) for coord in info["body"]],
#                 alive=info.get("alive", True),
#                 inventory=list(info.get("inventory", [])),
#                 active_effects=[
#                     ActiveEffectInfo(
#                         effect=effect["effect"],
#                         remaining_ticks=effect["remaining_ticks"],
#                     )
#                     for effect in info.get("active_effects", [])
#                 ],
#             )
#     return snakes

# @dataclass
# class Field:
#     size: Tuple[int, int]
#     snakes: Dict[TeamName, SnakeInfo]
#     apples: List[Coord] = field(default_factory=list)
#     bad_apples: List[Coord] = field(default_factory=list)

#     @staticmethod
#     def from_dict(raw: dict) -> "Field":
#         size = tuple(raw["size"])
        
#         source_snakes = raw.get("snakes", raw.get("snake", {}))
#         snakes = fill_info_from_source(source_snakes)

#         good_apples = []
#         bad_apples = []

#         # --- NEW HACKATHON SPECIFICATION PARSING ---
#         # Server transmits item tuples grouped under 'items'
#         raw_items = raw.get("items", [])
#         for item_entry in raw_items:
#             try:
#                 # Format: [[x, y], 'ItemType']
#                 coord_pair, item_type = item_entry[0], item_entry[1]
#                 coord = (coord_pair[0], coord_pair[1])
                
#                 if item_type == "BadApple":
#                     bad_apples.append(coord)
#                 elif item_type == "Apple":
#                     good_apples.append(coord)
#             except (IndexError, TypeError):
#                 continue

#         return Field(size=size, snakes=snakes, apples=good_apples, bad_apples=bad_apples)



# STARVATION
# import sys

# class SnakeProfile:
#     """
#     Tracks and normalizes individual snake structural attributes 
#     received from the game loop server telemetry tick.
#     """
#     def __init__(self, name, data, grid_size):
#         width, height = grid_size
#         self.name = name
#         self.id = data.get("id", "")
#         self.alive = data.get("alive", True)
        
#         # Pull raw coordinate arrays and strictly enforce map wrap-around bounds
#         raw_body = data.get("body", [])
#         self.body = [(int(pt[0]) % width, int(pt[1]) % height) for pt in raw_body]
        
#         # Head position shorthand helper
#         self.head = self.body[0] if self.body else (0, 0)
#         self.length = len(self.body)


# class Field:
#     """
#     Parses, maps, and structures the comprehensive arena environment matrix.
#     Natively splits multi-payload target configurations for Starvation execution modes.
#     """
#     def __init__(self, raw_json_data):
#         if not raw_json_data or not isinstance(raw_json_data, dict):
#             raise ValueError("Invalid matrix configuration initialization payload.")

#         # 1. Map Core Board Matrix Constraints
#         self.width = int(raw_json_data.get("width", 10))
#         self.height = int(raw_json_data.get("height", 10))
#         self.size = (self.width, self.height)
#         self.tick = int(raw_json_data.get("tick", 0))
        
#         # Backup list storage containing the unparsed item tuple structures
#         self.raw_items = raw_json_data.get("items", [])

#         # 2. Extract and Normalize Snake Profiles
#         self.snakes = {}
#         raw_snakes = raw_json_data.get("snakes", {})
#         for s_name, s_data in raw_snakes.items():
#             self.snakes[s_name] = SnakeProfile(s_name, s_data, self.size)

#         # 3. Dynamic Strategy Target Array Aggregation (Starvation Configuration)
#         self.apples = []
#         self.bad_apples = []
        
#         for item in self.raw_items:
#             try:
#                 # Server payload variant specs: [[x, y], "AppleType"] or [[x, y], {"type": "BadApple"}]
#                 coord_pair = item[0]
#                 item_meta = item[1]
                
#                 # Apply torus warp handling immediately during mapping extraction
#                 coord = (int(coord_pair[0]) % self.width, int(coord_pair[1]) % self.height)
                
#                 # Coerce meta signature typing seamlessly
#                 if isinstance(item_meta, str):
#                     item_type_str = item_meta.lower()
#                 else:
#                     item_type_str = str(item_meta.get("type", "")).lower()
                
#                 # Dynamic distribution separation routing
#                 if "bad" in item_type_str or "poison" in item_type_str:
#                     self.bad_apples.append(coord)
#                 else:
#                     self.apples.append(coord)
                    
#             except (IndexError, TypeError, KeyError):
#                 continue

#         # Legacy Fallback Routing: Fall back to native explicit arrays if unified payload is absent
#         if not self.apples and "apples" in raw_json_data:
#             self.apples = [(int(c[0]) % self.width, int(c[1]) % self.height) for c in raw_json_data["apples"]]
#         if not self.bad_apples and "bad_apples" in raw_json_data:
#             self.bad_apples = [(int(c[0]) % self.width, int(c[1]) % self.height) for c in raw_json_data["bad_apples"]]

#     def is_within_bounds(self, coord):
#         """Standard sanity check function (Note: board wraps around)."""
#         return 0 <= coord[0] < self.width and 0 <= coord[1] < self.height

#     def get_danger_matrix(self, current_length=2):
#         """
#         Utility mapping generation that instantly calculates a static 
#         lookup set containing all physical body segments across the arena.
#         """
#         occupied_cells = set()
#         for snake in self.snakes.values():
#             if not snake.alive:
#                 # Even dead or greyed-out snakes remain solid obstacles on the board
#                 for segment in snake.body:
#                     occupied_cells.add(segment)
#                 continue
                
#             for segment in snake.body:
#                 occupied_cells.add(segment)
                
#         # If snake size matches 1, inject bad apples directly into the dangerous terrain map
#         if current_length <= 1:
#             for bad_apple in self.bad_apples:
#                 occupied_cells.add(bad_apple)
                
#         return occupied_cells

# ==========================================================================================================================

# import sys

# class SnakeProfile:
#     """
#     Tracks and normalizes individual snake structural attributes 
#     received from the game loop server telemetry tick.
#     """
#     def __init__(self, name, data, grid_size):
#         width, height = grid_size
#         self.name = name
#         self.id = data.get("id", "")
#         self.alive = data.get("alive", True)
        
#         # Pull raw coordinate arrays and strictly enforce map wrap-around bounds
#         raw_body = data.get("body", [])
#         self.body = [(int(pt[0]) % width, int(pt[1]) % height) for pt in raw_body]
        
#         # Head position shorthand helper
#         self.head = self.body[0] if self.body else (0, 0)
#         self.length = len(self.body)


# class Field:
#     """
#     Parses, maps, and structures the comprehensive arena environment matrix.
#     Natively splits multi-payload target configurations for Starvation execution modes.
#     """
    
#     @classmethod
#     def from_dict(cls, raw_json_data):
#         """FIX: Factory method wrapper to support legacy itestra API client callers."""
#         return cls(raw_json_data)

#     def __init__(self, raw_json_data):
#         if not raw_json_data or not isinstance(raw_json_data, dict):
#             raise ValueError("Invalid matrix configuration initialization payload.")

#         # 1. Map Core Board Matrix Constraints
#         self.width = int(raw_json_data.get("width", 10))
#         self.height = int(raw_json_data.get("height", 10))
#         self.size = (self.width, self.height)
#         self.tick = int(raw_json_data.get("tick", 0))
        
#         # Backup list storage containing the unparsed item tuple structures
#         self.raw_items = raw_json_data.get("items", [])

#         # 2. Extract and Normalize Snake Profiles
#         self.snakes = {}
#         raw_snakes = raw_json_data.get("snakes", {})
#         for s_name, s_data in raw_snakes.items():
#             self.snakes[s_name] = SnakeProfile(s_name, s_data, self.size)

#         # 3. Dynamic Strategy Target Array Aggregation (Starvation Configuration)
#         self.apples = []
#         self.bad_apples = []
        
#         for item in self.raw_items:
#             try:
#                 # Server payload variant specs: [[x, y], "AppleType"] or [[x, y], {"type": "BadApple"}]
#                 coord_pair = item[0]
#                 item_meta = item[1]
                
#                 # Apply torus warp handling immediately during mapping extraction
#                 coord = (int(coord_pair[0]) % self.width, int(coord_pair[1]) % self.height)
                
#                 # Coerce meta signature typing seamlessly
#                 if isinstance(item_meta, str):
#                     item_type_str = item_meta.lower()
#                 else:
#                     item_type_str = str(item_meta.get("type", "")).lower()
                
#                 # Dynamic distribution separation routing
#                 if "bad" in item_type_str or "poison" in item_type_str:
#                     self.bad_apples.append(coord)
#                 else:
#                     self.apples.append(coord)
                    
#             except (IndexError, TypeError, KeyError):
#                 continue

#         # Legacy Fallback Routing: Fall back to native explicit arrays if unified payload is absent
#         if not self.apples and "apples" in raw_json_data:
#             self.apples = [(int(c[0]) % self.width, int(c[1]) % self.height) for c in raw_json_data["apples"]]
#         if not self.bad_apples and "bad_apples" in raw_json_data:
#             self.bad_apples = [(int(c[0]) % self.width, int(c[1]) % self.height) for c in raw_json_data["bad_apples"]]

#     def is_within_bounds(self, coord):
#         """Standard sanity check function (Note: board wraps around)."""
#         return 0 <= coord[0] < self.width and 0 <= coord[1] < self.height

#     def get_danger_matrix(self, current_length=2):
#         """
#         Utility mapping generation that instantly calculates a static 
#         lookup set containing all physical body segments across the arena.
#         """
#         occupied_cells = set()
#         for snake in self.snakes.values():
#             if not snake.alive:
#                 # Even dead or greyed-out snakes remain solid obstacles on the board
#                 for segment in snake.body:
#                     occupied_cells.add(segment)
#                 continue
                
#             for segment in snake.body:
#                 occupied_cells.add(segment)
                
#         # If snake size matches 1, inject bad apples directly into the dangerous terrain map
#         if current_length <= 1:
#             for bad_apple in self.bad_apples:
#                 occupied_cells.add(bad_apple)
                
#         return occupied_cells

# # Explicit class alias to maintain compatibility across different main script test setups
# SnakeField = Field


# STERN RAMPAGE
# import sys

# class SnakeProfile:
#     """
#     Tracks and normalizes individual snake structural attributes 
#     received from the game loop server telemetry tick.
#     """
#     def __init__(self, name, data, grid_size):
#         width, height = grid_size
#         self.name = name
#         self.id = data.get("id", "")
#         self.alive = data.get("alive", True)
        
#         # Pull raw coordinate arrays and strictly enforce map wrap-around bounds
#         raw_body = data.get("body", [])
#         self.body = [(int(pt[0]) % width, int(pt[1]) % height) for pt in raw_body]
        
#         # Head position shorthand helper
#         self.head = self.body[0] if self.body else (0, 0)
#         self.length = len(self.body)


# class Field:
#     """
#     Parses, maps, and structures the comprehensive arena environment matrix.
#     Natively supports invulnerability state mapping for Stern powerup execution.
#     """
    
#     @classmethod
#     def from_dict(cls, raw_json_data):
#         """Factory method wrapper to support legacy itestra API client callers."""
#         return cls(raw_json_data)

#     def __init__(self, raw_json_data):
#         if not raw_json_data or not isinstance(raw_json_data, dict):
#             raise ValueError("Invalid matrix configuration initialization payload.")

#         # 1. Map Core Board Matrix Constraints
#         self.width = int(raw_json_data.get("width", 10))
#         self.height = int(raw_json_data.get("height", 10))
#         self.size = (self.width, self.height)
#         self.tick = int(raw_json_data.get("tick", 0))
        
#         # Backup list storage containing the unparsed item tuple structures
#         self.raw_items = raw_json_data.get("items", [])

#         # 2. Extract and Normalize Snake Profiles
#         self.snakes = {}
#         raw_snakes = raw_json_data.get("snakes", {})
#         for s_name, s_data in raw_snakes.items():
#             self.snakes[s_name] = SnakeProfile(s_name, s_data, self.size)

#         # 3. Dynamic Strategy Target Array Aggregation
#         self.apples = []
#         self.bad_apples = []
#         self.star_powerups = []  # Tracks active Stern items on the ground
        
#         for item in self.raw_items:
#             try:
#                 coord_pair = item[0]
#                 item_meta = item[1]
                
#                 coord = (int(coord_pair[0]) % self.width, int(coord_pair[1]) % self.height)
                
#                 if isinstance(item_meta, str):
#                     item_type_str = item_meta.lower()
#                 else:
#                     item_type_str = str(item_meta.get("type", "")).lower()
                
#                 if "bad" in item_type_str or "poison" in item_type_str:
#                     self.bad_apples.append(coord)
#                 elif "stern" in item_type_str or "star" in item_type_str:
#                     self.star_powerups.append(coord)
#                 else:
#                     self.apples.append(coord)
                    
#             except (IndexError, TypeError, KeyError):
#                 continue

#         # Legacy Fallback Routing
#         if not self.apples and "apples" in raw_json_data:
#             self.apples = [(int(c[0]) % self.width, int(c[1]) % self.height) for c in raw_json_data["apples"]]
#         if not self.bad_apples and "bad_apples" in raw_json_data:
#             self.bad_apples = [(int(c[0]) % self.width, int(c[1]) % self.height) for c in raw_json_data["bad_apples"]]

#     def is_within_bounds(self, coord):
#         return 0 <= coord[0] < self.width and 0 <= coord[1] < self.height

#     def get_danger_matrix(self, current_length=2, is_invulnerable=False):
#         """
#         CRITICAL STERN MODIFICATION: If is_invulnerable is True, 
#         all snake bodies are completely removed from the danger matrix.
#         """
#         occupied_cells = set()
        
#         # If we have Stern active, we can phase through all bodies completely!
#         if is_invulnerable:
#             # Only treat bad apples as lethal if we are at length 1
#             if current_length <= 1:
#                 for bad_apple in self.bad_apples:
#                     occupied_cells.add(bad_apple)
#             return occupied_cells

#         # Standard safety mapping fallback when powerup is inactive
#         for snake in self.snakes.values():
#             for segment in snake.body:
#                 occupied_cells.add(segment)
                
#         if current_length <= 1:
#             for bad_apple in self.bad_apples:
#                 occupied_cells.add(bad_apple)
                
#         return occupied_cells

# # Explicit class alias
# SnakeField = Field