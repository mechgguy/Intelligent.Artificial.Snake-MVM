from typing import Dict, List, Tuple

class SnakeProfile:
    def __init__(self, data, grid_size):
        width, height = grid_size
        self.alive = data.get("alive", True)
        # Parse body as a list of [x, y]
        raw_body = data.get("body", [])
        self.body = [(int(pt[0]) % width, int(pt[1]) % height) for pt in raw_body]
        self.head = self.body[0] if self.body else (0, 0)

class Field:
    def __init__(self, raw_json_data):
        self.width = raw_json_data.get("width", 21)
        self.height = raw_json_data.get("height", 21)
        self.size = (self.width, self.height)
        
        # Parse Snakes
        self.snakes = {}
        raw_snakes = raw_json_data.get("snakes", {})
        for name, data in raw_snakes.items():
            self.snakes[name] = SnakeProfile(data, self.size)

        # Parse Items (Apples/BadApples) from "items" list
        self.apples = []
        self.bad_apples = []
        # Support the [[x, y], 'Type'] format
        for item in raw_json_data.get("items", []):
            try:
                coord = (item[0][0] % self.width, item[0][1] % self.height)
                kind = item[1]
                if "Bad" in kind:
                    self.bad_apples.append(coord)
                else:
                    self.apples.append(coord)
            except: continue

    @classmethod
    def from_dict(cls, data):
        return cls(data)

    def get_danger_matrix(self):
        occupied = set()
        for s in self.snakes.values():
            for segment in s.body:
                occupied.add(segment)
        for ba in self.bad_apples:
            occupied.add(ba)
        return occupied