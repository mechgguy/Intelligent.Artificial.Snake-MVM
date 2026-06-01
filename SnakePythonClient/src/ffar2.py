import math

class GreedyAgent:
    def __init__(self, size=32):
        self.size = size
        self.safety_buffer = 8  # Extra pixels/units to avoid tight gaps
        self.required_clearance = self.size + self.safety_buffer

    def get_distance(self, pos1, pos2):
        # Euclidean distance for smooth tracking
        return math.sqrt((pos1[0] - pos2[0])**2 + (pos1[1] - pos2[1])**2)

    def is_space_too_tight(self, target_pos, game_map):
        """
        Checks if the target position or gap is too small for the agent.
        Scans the bounding box around the target position.
        """
        half_clearance = self.required_clearance / 2
        
        # Define a bounding box around the potential next step
        left = int(target_pos[0] - half_clearance)
        right = int(target_pos[0] + half_clearance)
        top = int(target_pos[1] - half_clearance)
        bottom = int(target_pos[1] + half_clearance)
        
        # Check corners and sides of the required clearance area
        check_points = [
            (left, top), (right, top),
            (left, bottom), (right, bottom),
            (target_pos[0], top), (target_pos[0], bottom),
            (left, target_pos[1]), (right, target_pos[1])
        ]
        
        for pt in check_points:
            if game_map.is_wall_at(pt[0], pt[1]):
                return True  # Space is too cramped/narrow!
                
        return False

    def evaluate_next_step(self, current_pos, neighbors, sword_pos, game_map):
        best_score = float('inf')
        best_move = current_pos

        for neighbor in neighbors:
            # 1. Collision & Gap Filter
            if self.is_space_too_tight(neighbor, game_map):
                continue  # Reject this move entirely, it's a tight gap
            
            # 2. Greedy Scoring
            distance_to_sword = self.get_distance(neighbor, sword_pos)
            distance_from_start = self.get_distance(current_pos, neighbor)
            
            # Weigh the sword distance massively (Greedy approach)
            # Total Score = (1 * step_cost) + (10 * distance_to_sword)
            score = (1 * distance_from_start) + (10 * distance_to_sword)
            
            if score < best_score:
                best_score = score
                best_move = neighbor
                
        return best_move