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
from dataclasses import dataclass, field
from typing import Dict, List, Tuple

from data_structures import Coord, TeamName

@dataclass
class ActiveEffectInfo:
    effect: str
    remaining_ticks: int

@dataclass
class SnakeInfo:
    body: List[Coord]
    alive: bool
    inventory: List[str]
    active_effects: List[ActiveEffectInfo]

def fill_info_from_source(source) -> Dict[TeamName, SnakeInfo]:
    snakes = {}
    # Handle list format from server gracefully
    if isinstance(source, list):
        for idx, info in enumerate(source):
            team = info.get("name", f"Bot_{idx}")
            snakes[team] = SnakeInfo(
                body=[tuple(coord) for coord in info["body"]],
                alive=info.get("alive", True),
                inventory=list(info.get("inventory", [])),
                active_effects=[
                    ActiveEffectInfo(
                        effect=effect["effect"],
                        remaining_ticks=effect["remaining_ticks"],
                    )
                    for effect in info.get("active_effects", [])
                ],
            )
    # Handle standard dictionary format
    elif isinstance(source, dict):
        for team, info in source.items():
            snakes[team] = SnakeInfo(
                body=[tuple(coord) for coord in info["body"]],
                alive=info.get("alive", True),
                inventory=list(info.get("inventory", [])),
                active_effects=[
                    ActiveEffectInfo(
                        effect=effect["effect"],
                        remaining_ticks=effect["remaining_ticks"],
                    )
                    for effect in info.get("active_effects", [])
                ],
            )
    return snakes

@dataclass
class Field:
    size: Tuple[int, int]
    snakes: Dict[TeamName, SnakeInfo]
    apples: List[Coord] = field(default_factory=list)
    bad_apples: List[Coord] = field(default_factory=list)

    @staticmethod
    def from_dict(raw: dict) -> "Field":
        size = tuple(raw["size"])
        
        source_snakes = raw.get("snakes", raw.get("snake", {}))
        snakes = fill_info_from_source(source_snakes)

        good_apples = []
        bad_apples = []

        # --- NEW HACKATHON SPECIFICATION PARSING ---
        # Server transmits item tuples grouped under 'items'
        raw_items = raw.get("items", [])
        for item_entry in raw_items:
            try:
                # Format: [[x, y], 'ItemType']
                coord_pair, item_type = item_entry[0], item_entry[1]
                coord = (coord_pair[0], coord_pair[1])
                
                if item_type == "BadApple":
                    bad_apples.append(coord)
                elif item_type == "Apple":
                    good_apples.append(coord)
            except (IndexError, TypeError):
                continue

        return Field(size=size, snakes=snakes, apples=good_apples, bad_apples=bad_apples)