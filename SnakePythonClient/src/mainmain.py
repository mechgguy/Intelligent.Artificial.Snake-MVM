import argparse
import time
import random
from collections import deque

from api import SnakeFieldAPI
from data_structures import Direction, get_directions_as_list

def get_target_coord(current_head, direction, grid_size):
    """Calculates target coordinates handling map wrap-around rules."""
    x, y = current_head
    width, height = grid_size
    if direction == "NORTH": y = (y - 1) % height
    elif direction == "SOUTH": y = (y + 1) % height
    elif direction == "EAST":  x = (x + 1) % width
    elif direction == "WEST":  x = (x - 1) % width
    return (x, y)

def find_bfs_move(my_head, dangerous_cells, grid_size, apples):
    """
    Finds the first directional step towards the closest apple using BFS.
    If no apples are reachable, it returns None.
    """
    width, height = grid_size
    directions = ["NORTH", "SOUTH", "EAST", "WEST"]
    
    queue = deque([(my_head, [])])
    visited = {my_head}

    while queue:
        curr_pos, path = queue.popleft()
        
        if curr_pos in apples:
            if path:
                return path[0]

        for move in directions:
            next_pos = get_target_coord(curr_pos, move, grid_size)
            
            if next_pos not in dangerous_cells and next_pos not in visited:
                visited.add(next_pos)
                queue.append((next_pos, path + [move]))
                
    return None

def count_reachable_space(start_pos, dangerous_cells, grid_size, max_depth=60):
    """
    Counts how many free spaces are available from a starting position using Flood Fill.
    Prevents the snake from entering dead-ends or tight loops.
    """
    width, height = grid_size
    directions = ["NORTH", "SOUTH", "EAST", "WEST"]
    
    queue = deque([start_pos])
    visited = {start_pos}
    score = 0

    while queue and score < max_depth:
        curr = queue.popleft()
        score += 1
        
        for move in directions:
            next_pos = get_target_coord(curr, move, grid_size)
            if next_pos not in dangerous_cells and next_pos not in visited:
                visited.add(next_pos)
                queue.append(next_pos)
                
    return score   

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Snake game bot client")
    parser.add_argument("team_name", help="Name of the team/snake")
    parser.add_argument("game_name", help="Name of the game to join")
    parser.add_argument("--password", default="test", help="Password for server")
    parser.add_argument("--base_url", default="http://localhost:3030", help="Base URL")
    args = parser.parse_args()

    team_name = args.team_name
    base_url = args.base_url
    game_name = args.game_name
    password = args.password

    currentDirection: Direction = "EAST"
    api = SnakeFieldAPI(base_url, team_name, game_name, password)

    print(f"Connecting bot '{team_name}' to lobby '{game_name}'...")
    api.set_direction(currentDirection)

    while True:
        try:
            # 1. Fetch current tick matrix state
            field = api.get_field()
            
            my_snake = field.snakes.get(team_name)
            if not my_snake or not my_snake.alive:
                print("Snake waiting for spawn or dead... polling server.")
                time.sleep(1.0)
                continue

            my_head = my_snake.body[0]
            grid_size = field.size
            width, height = grid_size

            # 2. Map dangerous coordinates (obstacles)
            dangerous_cells = set()
            for s_name, s_info in field.snakes.items():
                if s_info.alive:
                    for block in s_info.body:
                        dangerous_cells.add(block)

            # 3. Filter immediately valid movements
            all_moves = get_directions_as_list()
            safe_moves = []
            for move in all_moves:
                next_pos = get_target_coord(my_head, move, grid_size)
                if next_pos not in dangerous_cells:
                    safe_moves.append(move)

            # 4. Generate dynamic food map (all empty spaces contain apples)
            apples = set()
            for x in range(width):
                for y in range(height):
                    coord = (x, y)
                    if coord not in dangerous_cells:
                        apples.add(coord)

            # 5. Hybrid Strategy Selection Engine
            chosen_move = None

            # Pathing Tier 1: Try to hunt down the closest food item via BFS
            if safe_moves and apples:
                chosen_move = find_bfs_move(my_head, dangerous_cells, grid_size, apples)

            # Pathing Tier 2: Survival Fallback via Flood Fill if trapped or no items found
            if not chosen_move and safe_moves:
                best_space_score = -1
                best_moves = []
                
                for move in safe_moves:
                    hypothetical_step = get_target_coord(my_head, move, grid_size)
                    space_score = count_reachable_space(hypothetical_step, dangerous_cells, grid_size)
                    
                    if space_score > best_space_score:
                        best_space_score = space_score
                        best_moves = [move]
                    elif space_score == best_space_score:
                        best_moves.append(move)
                
                if best_moves:
                    chosen_move = random.choice(best_moves)

            # 6. Execute move decisions safely
            if chosen_move:
                currentDirection = chosen_move
            elif safe_moves:
                currentDirection = safe_moves[0]
            else:
                print("No safe moves detected! Bracing for impact.")

            # Commit direction change to server
            api.set_direction(currentDirection)
            
            # Throttle loop to synchronize smoothly with 1-tick-per-second intervals
            time.sleep(0.4)

        except Exception as e:
            print(f"Network or parsing anomaly: {e}")
            time.sleep(1.0)